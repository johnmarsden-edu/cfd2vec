# Define the message passing functionality
# for use in the directed graph that I am 
# using
from __future__ import annotations
import torch
from torch.nn import Linear, Parameter
from torch_geometric.nn import MessagePassing
from torch_geometric.utils import add_remaining_self_loops, degree

class DirectedGCNConv(MessagePassing):
    def __init__(self, num_node_features: int, num_edge_features: int, num_output_features: int,):
        super().__init__(aggr = 'add')
        self.node_linear = Linear(num_node_features, num_output_features, bias = False)
        self.bias = Parameter(torch.zeros(num_output_features))

        self.edge_linear = Linear(num_edge_features, 1, bias = False)

    
    def reset_parameters(self):
        self.node_linear.reset_parameters()
        self.bias.data.zero_()

        self.edge_linear.reset_parameters()

    def forward(self, node_features: torch.Tensor, edge_index: torch.Tensor, edge_features: torch.Tensor, empty_edge_fill_value: torch.Tensor):
        # x has shape [N, num_node_features]
        # edge_index has shape [2, E]

        # Add self loops
        edge_index, possible_edge_features = add_remaining_self_loops(edge_index, edge_features, fill_value=empty_edge_fill_value)

        if possible_edge_features is not None:
            edge_features = possible_edge_features

        # Linearly transform the node feature matrix 
        node_features = self.node_linear(node_features)

        # Linearly transform the edge feature matrix into weights
        edge_weights = self.edge_linear(edge_features)

        # Compute normalization.
        row, col = edge_index
        deg = degree(col, node_features.size(0), dtype=node_features.dtype)
        deg_inv_sqrt = deg.pow(-0.5)
        deg_inv_sqrt[deg_inv_sqrt == float('inf')] = 0
        norm = deg_inv_sqrt[row] * deg_inv_sqrt[col]

        out = self.propagate(
            edge_index, 
            node_features=node_features, 
            norm=norm, edge_weights=edge_weights
        )

        out += self.bias

        return out


    def message(self, node_features_j: torch.Tensor, norm: torch.Tensor, edge_weights: torch.Tensor) -> torch.Tensor:
        return torch.mul(norm.view(-1, 1) * node_features_j, edge_weights)

    