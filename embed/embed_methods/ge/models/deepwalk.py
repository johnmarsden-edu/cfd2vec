# -*- coding:utf-8 -*-

"""



Author:

    Weichen Shen,wcshen1994@163.com



Reference:

    [1] Perozzi B, Al-Rfou R, Skiena S. Deepwalk: Online learning of social representations[C]//Proceedings of the 20th ACM SIGKDD international conference on Knowledge discovery and data mining. ACM, 2014: 701-710.(http://www.perozzi.net/publications/14_kdd_deepwalk.pdf)



"""
from __future__ import annotations
from typing import Iterable
from graph_tool import Graph
from ..walker import RandomWalker
from gensim.models import Word2Vec

class DeepWalk:
    def __init__(self, graphs: Iterable[Graph], walk_length: int, num_walks: int, workers: int = 1, get_node_data: bool = False):
        self.graphs = graphs
        self.w2v_model = None
        self._embeddings = {}
        self.get_node_data = get_node_data

        self.walk_args = {
            'num_walks': num_walks, 
            'walk_length': walk_length, 
            'workers': workers, 
            'verbosity_level': 1
        }

    def train(self, embed_size: int = 128, window_size: int = 5, workers: int = 3, epochs: int = 5, **kwargs: int | Iterable[int | str]):

        kwargs["sentences"] = self
        kwargs["min_count"] = kwargs.get("min_count", 0)
        kwargs["vector_size"] = embed_size
        kwargs["sg"] = 1  # skip gram
        kwargs["hs"] = 1  # deepwalk use Hierarchical Softmax
        kwargs["workers"] = workers
        kwargs["window"] = window_size
        kwargs["epochs"] = epochs

        print("Learning embedding vectors...")
        model = Word2Vec(**kwargs)
        print("Learning embedding vectors done!")

        self.w2v_model = model
        return model

    def __iter__(self):
        for graph in self.graphs:
            walker = RandomWalker(
                graph, p=1, q=1, get_node_data=self.get_node_data)
            print('Simulating walk over recently yielded graph')
            yield from walker.simulate_walks(**self.walk_args)

