# -*- coding:utf-8 -*-

"""



Author:

    Weichen Shen,wcshen1994@163.com



Reference:

    [1] Perozzi B, Al-Rfou R, Skiena S. Deepwalk: Online learning of social representations[C]//Proceedings of the 20th ACM SIGKDD international conference on Knowledge discovery and data mining. ACM, 2014: 701-710.(http://www.perozzi.net/publications/14_kdd_deepwalk.pdf)



"""
from ..walker import RandomWalker
from gensim.models import Word2Vec
import pandas as pd


class DeepWalk:
    def __init__(self, graph, walk_length, num_walks, workers=1, get_node_data=False):

        self.graph = graph
        self.w2v_model = None
        self._embeddings = {}
        self.get_node_data = get_node_data

        self.walker = RandomWalker(
            graph, p=1, q=1, get_node_data=get_node_data)
        self.sentences = self.walker.simulate_walks(
            num_walks=num_walks, walk_length=walk_length, workers=workers, verbose=1)

    def train(self, embed_size=128, window_size=5, workers=3, iter=5, **kwargs):

        kwargs["sentences"] = self.sentences
        kwargs["min_count"] = kwargs.get("min_count", 0)
        kwargs["vector_size"] = embed_size
        kwargs["sg"] = 1  # skip gram
        kwargs["hs"] = 1  # deepwalk use Hierarchical Softmax
        kwargs["workers"] = workers
        kwargs["window"] = window_size
        kwargs["epochs"] = iter

        print("Learning embedding vectors...")
        model = Word2Vec(**kwargs)
        print("Learning embedding vectors done!")

        self.w2v_model = model
        return model

    def get_embeddings(self,):
        if self.w2v_model is None:
            print("model not train")
            return {}

        self._embeddings = {}
        if self.get_node_data:
          for node in self.graph.nodes(data=True):
            self._embeddings[node[1]['data']] = self.w2v_model.wv[node[1]['data']]
        else:
          for word in self.graph.nodes():
              self._embeddings[word] = self.w2v_model.wv[word]

        return self._embeddings
