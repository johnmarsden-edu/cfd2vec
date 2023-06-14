from __future__ import annotations
import gc
from pathlib import Path
from typing import Iterable

from pickle import load
from graph_tool.all import Graph
from dotenv import load_dotenv

from embed_methods.ge import DeepWalk
from embed_methods.readers import GraphBatcher
# from embed_methods.ge import Node2Vec

load_dotenv('../.env')

EMBED_SIZE = 50


def get_models(strat: str, graph: Graph):
    yield (
            f"{strat}-deepwalk.model",
            DeepWalk(
                graph, walk_length=10, num_walks=20, workers=1, get_node_data=True
            ),
        )
    # yield (
    #         f"{strat}-node2vec.model",
    #         Node2Vec(
    #             graph,
    #             walk_length=10,
    #             num_walks=20,
    #             p=1.5,
    #             q=1.5,
    #             workers=1,
    #             get_node_data=True,
    #         ),
    #     )


def train_model(strat: str, embed_size: int, vector_dir: Path):
    print('Loading the graphs')
    graph = load_graphs(strat)

    for filename, model in get_models(strat, graph):
        print(f'Training the model that will be saved to {filename}')
        model.train(window_size=5, epochs=5, embed_size=embed_size, min_count=1, workers=1, hs=1, negative=0).save(str(vector_dir / filename))
        del model
        gc.collect()


STRATS: list[str] = ['NC', 'VC', 'LC', 'FC']


def train_models(embed_size: int):
    data_dir = Path.cwd() / ".." / "data"
    model_dir = data_dir / ".." / "models"
    model_dir.mkdir(parents=True, exist_ok=True)
    for strat in STRATS:
        print(f'Training a model for the strategy {strat}')
        train_model(strat, embed_size, model_dir)


def main():
    train_models(EMBED_SIZE)


GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs'
COMMIT_DIR = Path.cwd() / '..' / 'data' / 'graphs_metadata'

def load_graphs(strat: str) -> Iterable[Graph]:
    with open(COMMIT_DIR / f'{strat}_graphs_files_list.pkl', 'rb') as graphs_files_list_pickle:
        cfl: list[str] = load(graphs_files_list_pickle)
        return GraphBatcher(cfl, GRAPH_DIR, 5000)


if __name__ == "__main__":
    print('Starting to train the embedding models')
    main()

# %%
