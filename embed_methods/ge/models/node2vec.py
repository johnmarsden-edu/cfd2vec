# -*- coding:utf-8 -*-

"""



Author:

    Weichen Shen,wcshen1994@163.com



Reference:

    [1] Grover A, Leskovec J. node2vec: Scalable feature learning for networks[C]//Proceedings of the 22nd ACM SIGKDD international conference on Knowledge discovery and data mining. ACM, 2016: 855-864.(https://www.kdd.org/kdd2016/papers/files/rfp0218-groverA.pdf)



"""

from gensim.models import Word2Vec
import pandas as pd

from ..walker import RandomWalker


class Node2Vec:

    def __init__(self, graph, walk_length, num_walks, p=1.0, q=1.0, workers=1, use_rejection_sampling=0, get_node_data=True):

        self.graph = graph
        self._embeddings = {}
        self.get_node_data = get_node_data
        self.walker = RandomWalker(
            graph, p=p, q=q, use_rejection_sampling=use_rejection_sampling, get_node_data=get_node_data)

        print("Preprocess transition probs...")
        self.walker.preprocess_transition_probs()

        self.sentences = self.walker.simulate_walks(
            num_walks=num_walks, walk_length=walk_length, workers=workers, verbose=1)

    def train(self, embed_size=128, window_size=5, workers=3, iter=5, **kwargs):

        kwargs["sentences"] = self.sentences
        kwargs["min_count"] = kwargs.get("min_count", 0)
        kwargs["vector_size"] = embed_size
        kwargs["sg"] = 1
        kwargs["hs"] = 0  # node2vec not use Hierarchical Softmax
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
