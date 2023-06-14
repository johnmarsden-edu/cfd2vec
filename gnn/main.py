from __future__ import annotations
from typing import Callable, Union, Tuple

# Torch
import torch

# Dataset 
from gnn.graph_reader import GraphToolDataset, PytorchDataset
from gnn.node2vec import Node2Vec
from torchtext.vocab import Vocab
from torch_geometric.loader import DataLoader

# Special Data
from torch_geometric.data import Data

# Dataset import libs
from pathlib import Path
from pickle import load

# Utility functions for debugging
from torch_geometric.utils import to_networkx # type: ignore
from networkx.drawing.nx_agraph import write_dot # type: ignore

# Fix windows so that it correctly handles ansi escape codes
import colorama

# Use this to correctly format batch num information
import math

# Time and time formatting libraries for recording training time.
import datetime as dt
from time import perf_counter
import humanize

# Data reporting and hyperparameter tuning
import numpy as np
import wandb
import optuna
from optuna.trial import Trial
import os
import sys

# Suppress useless warning
import warnings
import faulthandler

# Include secrets
from dotenv import load_dotenv

# Argument parsing
import click

# Environment Validation
import multiprocessing
from multiprocessing import Process
from multiprocessing.queues import Queue
from threading import Thread

colorama.init()

faulthandler.enable()

warnings.filterwarnings("ignore", r"TypedStorage is deprecated.*", UserWarning, r"torch_geometric\.data\.collate.*", 145)

class StdoutQueue(Queue):
    def __init__(self,*args,**kwargs):
        ctx = multiprocessing.get_context()
        super(StdoutQueue, self).__init__(*args, **kwargs, ctx=ctx)
    def write(self,msg):
        self.put(msg)
    def flush(self):
        self.put(None)

def output_writer(sq: StdoutQueue):
    while True:
        item = sq.get()
        if item == 'kill_writer':
            break
        elif item:
            sys.stdout.write(item)
        else:
            sys.stdout.flush()

def define_model(node_vocab: Vocab, p: float, q: float, device: str):

    print(f'{len(node_vocab)=}')
    n2v = Node2Vec(
        num_embeddings=len(node_vocab),
        node_embedding_dim=128,
        walk_length=10,
        context_size=5,
        walks_per_node=10,
        num_negative_samples=2,
        sparse=True,
        p=p,
        q=q
    )
    n2v.to(device)

    print(f'{n2v.node_embedding.num_embeddings=}')
    return n2v


def define_preprocessed_data(vocab_dir: Path, graph_metadata_dir: Path, strat: str) -> Tuple[Vocab, Vocab, Vocab, list[str]]:
    with open(vocab_dir / f'{strat}_node_vocab.pkl', 'rb') as node_vocab_pickle, \
         open(vocab_dir / f'{strat}_edge_vocab.pkl', 'rb') as edge_vocab_pickle, \
         open(vocab_dir / f'{strat}_graph_name_vocab.pkl', 'rb') as graph_name_vocab_pickle, \
         open(graph_metadata_dir / f'{strat}_graphs_files_list.pkl', 'rb') as graphs_files_list_pickle:
        
        node_vocab: Vocab = load(node_vocab_pickle)
        edge_vocab: Vocab = load(edge_vocab_pickle)
        graph_name_vocab: Vocab = load(graph_name_vocab_pickle)
        graphs_files_list: list[str] = load(graphs_files_list_pickle)

    print(f'{len(node_vocab)=}')
    return node_vocab, edge_vocab, graph_name_vocab, graphs_files_list


def define_dataloaders(graph_dir: Path, node_vocab: Vocab, edge_vocab: Vocab, graph_name_vocab: Vocab, graphs_files_list: list[str], strat: str) -> Union[Tuple[DataLoader, DataLoader, DataLoader], Tuple[list[int], list[int], list[int]]]:
    # create graph ml dataset
    train_ds = PytorchDataset(graph_dir / 'batches' / strat / 'train')
    val_ds = PytorchDataset(graph_dir / 'batches' / strat / 'validation')
    test_ds = PytorchDataset(graph_dir / 'batches' / strat / 'test')
    print(f'There are {len(node_vocab)} nodes in our node vocab')
    print(f'There are {len(edge_vocab)} edges in our edge vocab')
    print(f'There are {len(graph_name_vocab)} graph names in our graph name vocab')

    # # feed the dataset into the pyg dataloader
    train_dataloader = DataLoader(train_ds, batch_size=1, shuffle=True, persistent_workers=True, prefetch_factor=4, num_workers=4)
    validation_dataloader = DataLoader(val_ds, batch_size=1, shuffle=True, persistent_workers=True, prefetch_factor=4, num_workers=4)
    test_dataloader = DataLoader(test_ds, batch_size=1, shuffle=True, persistent_workers=True, prefetch_factor=4, num_workers=4)

    return train_dataloader, validation_dataloader, test_dataloader


def objective(strat: str, graph_dir: Path, vocab_dir: Path, graph_metadata_dir: Path, model_dir: Path) -> float:
    learning_rate = 0.1
    # PQ_UPPER_BOUND = 0 # 2
    p = 1 # 2**trial.suggest_int('p_exp', -2, PQ_UPPER_BOUND)
    q = 1 # 2**trial.suggest_int('q_exp', -2, PQ_UPPER_BOUND)

    node_vocab, edge_vocab, graph_name_vocab, graphs_files_list = define_preprocessed_data(vocab_dir, graph_metadata_dir, strat)

    train_dataloader, validation_dataloader, _test_dataloader = define_dataloaders(graph_dir, node_vocab, edge_vocab, graph_name_vocab, graphs_files_list, strat)

    device = 'cuda' if torch.cuda.is_available() else 'cpu'

    print(f'Creating the {strat} node2vec model now')
    n2v = define_model(node_vocab, p, q, device)

    optim = torch.optim.SparseAdam(list(n2v.parameters()), lr=learning_rate)
    
    def train(epoch: int):
        n2v.train()
        total_loss = 0
        total_batches = len(train_dataloader)
        right_align = round(math.log10(total_batches)) + 2
        for batch_num, data in enumerate(train_dataloader, 1):
            print(f'Batch: {batch_num:>{right_align}}/{total_batches}')
            pos_walks, neg_walks = n2v.sample(data.to(device), device)
            optim.zero_grad()
            loss = n2v.loss(data, pos_walks.to(device), neg_walks.to(device))
            loss.backward()
            optim.step()
            wandb.log({'training_loss': loss.item(), 'epoch': epoch, 'batch_num': batch_num})
            total_loss += loss.item()
        print()
        return total_loss / len(train_dataloader)

    @torch.no_grad()
    def evaluate(epoch: int, eval_target: str, dl_evalute: DataLoader):
        n2v.eval()
        total_loss = 0
        total_batches = len(dl_evalute)
        right_align = round(math.log10(total_batches)) + 2
        for batch_num, data in enumerate(dl_evalute):
            print(f'Batch: {batch_num:>{right_align}}/{total_batches}')
            pos_walks, neg_walks = n2v.sample(data.to(device), device)
            optim.zero_grad()
            loss = n2v.loss(data, pos_walks.to(device), neg_walks.to(device))
            wandb.log({'eval_target': eval_target, f'{eval_target}_loss': loss.item(), 'epoch': epoch, 'batch_num': batch_num})
            total_loss += loss.item()
        print()
        return total_loss / len(dl_evalute)
    
    validation_loss = 0.0
    for epoch in range(10):
        epoch_start_time = perf_counter()

        print(f'Begin epoch {epoch + 1} training')
        training_start_time = perf_counter()
        train_loss = train(epoch)
        training_end_time = perf_counter()
        
        print(f'Begin epoch {epoch + 1} validation')
        validation_start_time = perf_counter()
        validation_loss = evaluate(epoch, 'validation', validation_dataloader)
        validation_end_time = perf_counter()

        epoch_end_time = perf_counter()
        
        training_timedelta = dt.timedelta(seconds=training_end_time-training_start_time)
        validation_timedelta = dt.timedelta(seconds=validation_end_time-validation_start_time)
        epoch_timedelta = dt.timedelta(seconds=epoch_end_time-epoch_start_time)
        print(f'Epoch: {epoch + 1}, Epoch Time: {humanize.precisedelta(epoch_timedelta)}\n' + 
              f'Training Time: {humanize.precisedelta(training_timedelta)}, Training Loss: {train_loss}\n'
              f'Validation Time: {humanize.precisedelta(validation_timedelta)}, {validation_loss=}')
        
        wandb.log({'epoch': epoch, 'training_time': training_timedelta.total_seconds(), 'validation_time': validation_timedelta.total_seconds(), 'epoch_time': epoch_timedelta.total_seconds(), 'mean_training_loss': train_loss, 'mean_validation_loss': validation_loss})

        cur_model_dir = model_dir / strat / f'lr-{learning_rate}' / f'p-{p}' / f'q-{q}'
        cur_model_dir.mkdir(parents=True, exist_ok=True)

        model_save_dict = {
            'num_embeddings': n2v.node_embedding.num_embeddings,
            'node_embedding_dim': n2v.node_embedding_dim,
            'walk_length': n2v.walk_length,
            'context_size': n2v.context_size,
            'walks_per_node': n2v.walks_per_node,
            'num_negative_samples': n2v.num_negative_samples,
            'sparse': n2v.node_embedding.sparse,
            'p': n2v.p,
            'q': n2v.q,
            'state_dict': n2v.state_dict()
        }

        if (epoch + 1) % 10 == 0:
            torch.save(n2v.state_dict(), str((cur_model_dir / f'embedding-model-save-dict.{epoch + 1}.tar').resolve()))

        torch.save(model_save_dict, str((cur_model_dir / 'embedding-model-save-dict.tar').resolve()))


    # test_start_time = perf_counter()
    # test_loss = evaluate(epoch, 'validation', validation_dataloader)
    # test_end_time = perf_counter()
    # testing_timedelta = dt.timedelta(seconds=test_end_time-test_start_time)
    # wandb.log({'testing_time': testing_timedelta.total_seconds(), 'mean_test_loss': test_loss})
    return validation_loss

def dummy(ree):
    return 'ree'

def run_study(sq_out: StdoutQueue, sq_err: StdoutQueue, proc_num: int, hpc: bool, dotenv: str, sync_file: str, job_id: int, job_array_index: int, canon_strategy: str, graph_dir: str, model_dir: str, vocab_dir: str, metadata_dir: str):
    sys.stdout = sq_out
    sys.stderr = sq_err

    torch.cuda.set_device(proc_num)
    load_dotenv(dotenv)

    project = f'cfg2vec_{canon_strategy}_node_embedding' + ('_hpc' if hpc else '')

    if hpc:
        wandb.init(project=project, name=f'job-{job_id}-index-{job_array_index}')
    else:
        wandb.init(project=project)

    graph_dir_path = Path(graph_dir)
    model_dir_path = Path(model_dir)
    vocab_dir_path = Path(vocab_dir)
    metadata_dir_path = Path(metadata_dir)

    objective(canon_strategy, graph_dir_path, vocab_dir_path, metadata_dir_path, model_dir_path)

def main():
    cli()

if __name__ == '__main__':
    @click.group()
    @click.option('--num-cores', type=click.INT, default=None, help='Number of cores available')
    @click.pass_context
    def cli(ctx, num_cores: int):
        ctx.ensure_object(dict)

        ctx.obj['NUM_CORES'] = num_cores if num_cores else multiprocessing.cpu_count()

    @cli.command('test-hpc-ready')
    @click.pass_context
    def test_hpc_ready(ctx):
        print(f'{torch.cuda.is_available()=}')
        print(f'{ctx.obj["NUM_CORES"]=}')

    @cli.command('run')
    @click.option('-e', '--dotenv', required=True, default=None, type=click.STRING,help='Path to dotenv file loaded in with secrets')
    @click.option('-s', '--sync-file', required=True, default=None, type=click.STRING,help='Path to the journal file for optuna that is used for synchronizing studies')
    @click.option('-c', '--canon-strategy', required=True, type=click.Choice(['NC', 'LC', 'VC', 'FC']), help='Canonicalization strategy for this model')
    @click.option('-g', '--graph-dir', required=True, type=click.STRING, help='The directory where the generated graphs are')
    @click.option('-v', '--vocab-dir', required=True, type=click.STRING, help='The directory where the vocabs are stored')
    @click.option('-d', '--metadata-dir', required=True, type=click.STRING, help='The directory where the graph metadata is stored')
    @click.option('-m', '--model-dir', required=True, type=click.STRING, help='The directory where the models are to be stored')
    @click.option('--hpc', is_flag=True, help='Use if this version is going to be run on the HPC')
    @click.option('-j', '--job-id', default=0, type=click.INT, help='Job ID for the HPC')
    @click.option('-i', '--job-array-index', default=-1, type=click.INT, help='Job Array Index ID for the HPC')
    @click.pass_context
    def start_studies(ctx, hpc: bool, dotenv: str, sync_file: str, job_id: int, job_array_index: int, canon_strategy: str, graph_dir: str, model_dir: str, vocab_dir: str, metadata_dir: str):
        multiprocessing.set_start_method('spawn')
        stdout_q = StdoutQueue()
        stderr_q = StdoutQueue()

        print('Creating output worker')
        stdout_worker = Process(target=output_writer, args=(stdout_q,))
        print('Creating error worker')
        stderr_worker = Process(target=output_writer, args=(stderr_q,))

        print('Creating study 1')
        
        s1_args = (stdout_q, stderr_q, 0, hpc, dotenv, sync_file, job_id, job_array_index, canon_strategy, graph_dir, model_dir, vocab_dir, metadata_dir)
        study_1 = Process(target=run_study, args=s1_args)
        if hpc:
            print('Creating study 2')
            s2_args = (stdout_q, stderr_q, 1, hpc, dotenv, sync_file, job_id, job_array_index, canon_strategy, graph_dir, model_dir, vocab_dir, metadata_dir)
            study_2 = Process(target=run_study, args=s2_args)


        print('Starting output worker')
        stdout_worker.start()

        print('Starting stderr worker')
        stderr_worker.start()

        print('Starting study 1')
        study_1.start()
        if hpc:
            print('Starting study 2')
            study_2.start()

        print('Waiting for study 1 to finish')
        study_1.join()
        if hpc:
            print('Waiting for study 2 to finish')
            study_2.join()
        print('Waiting for output workers, stdout queue, and stderr queue to be finished processing')
        stdout_q.put('kill_writer')
        stdout_worker.join()

        stderr_q.put('kill_writer')
        stderr_worker.join()
    main()