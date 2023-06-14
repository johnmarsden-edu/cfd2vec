from gnn.graph_reader import CodeWorkoutDataset
from pickle import load
import click
from pathlib import Path
from gnn.node2vec import Node2Vec
from typing import Union, Any
import torch
import pandas as pd
import pickle

GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs'
MODEL_DIR = Path.cwd() / '..' / 'models'
STRATS = ['NC', 'LC', 'VC', 'FC']


@click.command()
@click.argument('strat', type=click.STRING)
@click.argument('learning_rate', type=click.FLOAT)
@click.option('--epoch', required=False, type=click.INT, default=None)
def embed_programs(strat: str, learning_rate: float, epoch: Union[int, None]):

    def metadata_load(filename):
        with open(filename, 'rb') as pickled_obj:
            return pickle.load(pickled_obj)
    
    best_score_data = metadata_load('student_metadata/best_score_data.pkl')
    
    device = 'cpu'


    cur_model_save_loc = MODEL_DIR / strat / f'lr-{learning_rate}' / f'p-1' / f'q-1'
    if epoch:
        cur_model_save_loc = cur_model_save_loc / f'embedding-model-save-dict.{epoch}.tar'
    else:
        cur_model_save_loc = cur_model_save_loc / 'embedding-model-save-dict.tar'
    model_save_dict: dict[str, Any] = torch.load(str(cur_model_save_loc.resolve()), map_location=torch.device(device))
    n2v = Node2Vec(
        num_embeddings=model_save_dict['num_embeddings'],
        node_embedding_dim=model_save_dict['node_embedding_dim'],
        walk_length=model_save_dict['walk_length'],
        context_size=model_save_dict['context_size'],
        walks_per_node=model_save_dict['walks_per_node'],
        num_negative_samples=model_save_dict['num_negative_samples'],
        sparse=model_save_dict['sparse'],
        p=model_save_dict['p'],
        q=model_save_dict['q']
    )

    n2v.load_state_dict(model_save_dict['state_dict'])
    n2v.to(device)
    dataset = CodeWorkoutDataset(GRAPH_DIR, best_score_data, strat)
    
    with torch.no_grad():
        n2v.eval()
        all_embeddings = []
        codestate_ids = []
        for program_id, program_data in dataset:
            if program_data is None:
                all_embeddings.append(torch.zeros(128).numpy())
            else:
                embeddings = n2v(program_data.x)
                program_embedding = torch.mean(embeddings, 0)
                all_embeddings.append(program_embedding.numpy())
            
            codestate_ids.append(program_id)
        
        program_embeddings = pd.DataFrame(all_embeddings)
        program_embeddings['CodeStateID'] = codestate_ids
        print(program_embeddings.head())
        vector_path = Path(f'../data/vectors/{strat}-vectors.csv')
        vector_path.parent.mkdir(exist_ok=True, parents=True)
        program_embeddings.to_csv(vector_path, index=False)



if __name__ == '__main__':
    embed_programs()