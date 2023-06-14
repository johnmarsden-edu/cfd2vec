from __future__ import annotations
from typing import Callable, Union, Tuple

# Dataset 
from gnn.graph_reader import GraphToolDataset
from torchtext.vocab import Vocab
from torch_geometric.loader import DataLoader
import torch

# Special Data
from torch_geometric.data import Data

# Dataset import libs
from pathlib import Path
from pickle import load

# Argument parsing
import click

# Multiprocessing
from multiprocessing import Pool, log_to_stderr, Process
import logging

def define_preprocessed_data(vocab_dir: Path, graph_metadata_dir: Path, strat: str, logger) -> Tuple[Vocab, Vocab, Vocab, list[str]]:
    with open(vocab_dir / f'{strat}_node_vocab.pkl', 'rb') as node_vocab_pickle, \
         open(vocab_dir / f'{strat}_edge_vocab.pkl', 'rb') as edge_vocab_pickle, \
         open(vocab_dir / f'{strat}_graph_name_vocab.pkl', 'rb') as graph_name_vocab_pickle, \
         open(graph_metadata_dir / f'{strat}_graphs_files_list.pkl', 'rb') as graphs_files_list_pickle:
        
        logger.info(f'Loading {strat} node vocab')
        node_vocab: Vocab = load(node_vocab_pickle)
        logger.info(f'Loading {strat} edge vocab')
        edge_vocab: Vocab = load(edge_vocab_pickle)
        logger.info(f'Loading {strat} graph name vocab')
        graph_name_vocab: Vocab = load(graph_name_vocab_pickle)
        logger.info(f'Loading {strat} graphs files list')
        graphs_files_list: list[str] = load(graphs_files_list_pickle)

    logger.info(f'{len(node_vocab)=}')
    return node_vocab, edge_vocab, graph_name_vocab, graphs_files_list

def process_dataset(graph_dir: Path, node_vocab: Vocab, edge_vocab: Vocab, graph_name_vocab: Vocab, graphs_files_list: list[str], strat: str, logger):
    # create graph ml dataset
    logger.info(f'Loading {strat} dataset')
    dataset = GraphToolDataset(graph_dir, node_vocab, edge_vocab, graph_name_vocab, graphs_files_list)
    logger.info(f'There are {len(node_vocab)} nodes in our node vocab')
    logger.info(f'There are {len(edge_vocab)} edges in our edge vocab')
    logger.info(f'There are {len(graph_name_vocab)} graph names in our graph name vocab')

    batch_size = 128

    dataset = dataset.shuffle()

    if isinstance(dataset, tuple):
        dataset = dataset[0]

    train_ds_len = int(len(dataset) * .7)
    val_ds_len = int(len(dataset) * .1)
    test_ds_len = len(dataset) - train_ds_len - val_ds_len

    print(f'Length of dataset before splitting: {len(dataset)}')

    print(f'Length of training dataset: {train_ds_len}')
    train_dataset = dataset[:train_ds_len]

    if isinstance(train_dataset, Data):
        return ([], [], [])

    print(f'Length of validation dataset: {val_ds_len}')
    validation_dataset = dataset[train_ds_len:train_ds_len + val_ds_len]

    if isinstance(validation_dataset, Data):
        return ([], [], [])

    print(f'Length of test dataset: {test_ds_len}')
    test_dataset = dataset[train_ds_len + val_ds_len:]

    if isinstance(test_dataset, Data):
        return ([], [], [])

    print(f'Length of all 3 datasets: {train_ds_len} + {val_ds_len} + {test_ds_len} = {train_ds_len + val_ds_len + test_ds_len}')

    # # feed the dataset into the pyg dataloader
    train_dataloader = DataLoader(train_dataset, batch_size=batch_size, shuffle=True, num_workers=4)
    validation_dataloader = DataLoader(validation_dataset, batch_size=batch_size, shuffle=False, num_workers=4)
    test_dataloader = DataLoader(test_dataset, batch_size=batch_size, shuffle=False, num_workers=4)

    return train_dataloader, validation_dataloader, test_dataloader

def preprocess(strat: str, graph_dir: Path, vocab_dir: Path, graph_metadata_dir: Path):
    logger = log_to_stderr()
    logger.setLevel(logging.INFO)

    logger.info(f'Get {strat} preprocessed data')
    node_vocab, edge_vocab, graph_name_vocab, graphs_files_list = define_preprocessed_data(vocab_dir, graph_metadata_dir, strat, logger)

    logger.info(f'Process {strat} data')
    train_dataloader, validation_dataloader, test_dataloader = process_dataset(graph_dir, node_vocab, edge_vocab, graph_name_vocab, graphs_files_list, strat, logger)

    batched_data_dir = graph_dir / 'batches' / strat

    def batch_data_loader(dl, target_dir):
        target_dir.mkdir(parents=True, exist_ok=True)
        for batch_num, data in enumerate(dl):
            logger.info(f'{strat} - {target_dir.name} - Processing batch {batch_num}')
            torch.save(data, target_dir / f'batch.{batch_num:04d}.pt')

    # train_dir = batched_data_dir / 'train'
    # batch_data_loader(train_dataloader, train_dir)

    if strat == 'VC':
        validation_dir = batched_data_dir / 'validation'
        batch_data_loader(validation_dataloader, validation_dir)

    test_dir = batched_data_dir / 'test'
    batch_data_loader(test_dataloader, test_dir)

if __name__ == '__main__':
    STRATS = ['FC', 'VC', 'NC', 'LC']
    @click.command()
    @click.option('-g', '--graph-dir', required=True, type=click.STRING, help='The directory where the generated graphs are')
    @click.option('-v', '--vocab-dir', required=True, type=click.STRING, help='The directory where the vocabs are stored')
    @click.option('-d', '--metadata-dir', required=True, type=click.STRING, help='The directory where the graph metadata is stored')
    def start_preprocessing(graph_dir: str, vocab_dir: str, metadata_dir: str):
        print('Starting preprocessing')
        graph_dir_path = Path(graph_dir)
        vocab_dir_path = Path(vocab_dir)
        metadata_dir_path = Path(metadata_dir)

        processes = []
        for strat in STRATS:
            print(f'Creating {strat} preprocessing process now')
            pre_args = (strat, graph_dir_path, vocab_dir_path, metadata_dir_path) 
            processes.append(Process(target=preprocess, args=pre_args))

        for strat, pr in zip(STRATS, processes):
            print(f'Starting {strat} preprocessing process now')
            pr.start()

        for strat, pr in zip(STRATS, processes):
            print(f'Waiting for {strat} preprocessing process to finish')
            pr.join()
        
    start_preprocessing()
