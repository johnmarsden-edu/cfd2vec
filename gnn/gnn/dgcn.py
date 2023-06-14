# Define the directed graph convolutional neural 
# network model that I will train to make
# predictions about student misconceptions

# The model should do the following 

# First, it should use some kind of trainable
# vocab embedding process to convert the node 
# data into embeddings

# Second, it should do some kind of convolution
# or other operation on the data and shrink the
# nodes down repeatedly until we hit the number
# misconceptions that we want to predict

# Third, it should do some kind of softmax or
# something to determine probabilities that a 
# student has a particular misconception.
from __future__ import annotations
import torch
import torch.nn.functional as F
import torch_geometric.nn as tgnn
from torch_geometric.data import Batch
from gnn.message import DirectedGCNConv

class LearnableEmbeddings(torch.nn.Module):
    def __init__(
        self, 
        node_vocab_size: int, 
        node_embedding_dim: int,
        edge_vocab_size: int,
        edge_embedding_dim: int,
        empty_edge_index: int,
        intermediate_dim: int,
        num_graph_names: int,
        ) -> None:
        super().__init__()
        self.embed_node = torch.nn.Embedding(node_vocab_size, node_embedding_dim)
        self.embed_edge = torch.nn.Embedding(edge_vocab_size, edge_embedding_dim)
        self.conv1 = DirectedGCNConv(node_embedding_dim, edge_embedding_dim, intermediate_dim)
        self.conv2 = DirectedGCNConv(intermediate_dim, edge_embedding_dim, num_graph_names)
        self.empty_edge_index = empty_edge_index

    def forward(self, data: Batch):
        node_features, edge_index, edge_features = self.embed_node(data.x), data.edge_index, self.embed_edge(data.edge_attr)

        empty_edge_embedding = self.embed_edge(torch.tensor([self.empty_edge_index], dtype=torch.int32))

        intermediate_features = self.conv1(node_features, edge_index, edge_features, empty_edge_embedding)
        intermediate_features = F.relu6(intermediate_features)
        intermediate_features = F.dropout(intermediate_features, training=self.training)
        final_features = self.conv2(intermediate_features, edge_index, edge_features, empty_edge_embedding)
        final_features = F.relu(final_features)
        final_features = F.dropout(final_features)
        final_features = tgnn.global_mean_pool(final_features, data.batch)

        return F.log_softmax(final_features, dim=1)

