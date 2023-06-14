from typing import Optional, Tuple, Union

import torch
from torch import Tensor
from torch.nn import Embedding

from torch_geometric.typing import WITH_PYG_LIB
from torch_geometric.data import DataLoader, Data
from torchtext.vocab import Vocab
import sys
from termcolor import colored

try: 
    import torch_cluster  # noqa 
    WITH_TORCH_CLUSTER = True 
except (ImportError, OSError) as e: 
    if isinstance(e, OSError): 
        warnings.warn(f"An issue occurred while importing 'torch-cluster'. " 
                    f"Disabling its usage. Stacktrace: {e}") 
    WITH_TORCH_CLUSTER = False 


class Node2Vec(torch.nn.Module):
    r"""The Node2Vec model from the
    `"node2vec: Scalable Feature Learning for Networks"
    <https://arxiv.org/abs/1607.00653>`_ paper where random walks of
    length :obj:`walk_length` are sampled in a given graph, and node embeddings
    are learned via negative sampling optimization.

    .. note::

        For an example of using Node2Vec, see `examples/node2vec.py
        <https://github.com/pyg-team/pytorch_geometric/blob/master/examples/
        node2vec.py>`_.

    Args:
        edge_index (torch.Tensor): The edge indices.
        embedding_dim (int): The size of each embedding vector.
        walk_length (int): The walk length.
        context_size (int): The actual context size which is considered for
            positive samples. This parameter increases the effective sampling
            rate by reusing samples across different source nodes.
        walks_per_node (int, optional): The number of walks to sample for each
            node. (default: :obj:`1`)
        p (float, optional): Likelihood of immediately revisiting a node in the
            walk. (default: :obj:`1`)
        q (float, optional): Control parameter to interpolate between
            breadth-first strategy and depth-first strategy (default: :obj:`1`)
        num_negative_samples (int, optional): The number of negative samples to
            use for each positive sample. (default: :obj:`1`)
        num_nodes (int, optional): The number of nodes. (default: :obj:`None`)
        sparse (bool, optional): If set to :obj:`True`, gradients w.r.t. to the
            weight matrix will be sparse. (default: :obj:`False`)
    """
    def __init__(
        self,
        num_embeddings: int,
        node_embedding_dim: int,
        walk_length: int,
        context_size: int,
        walks_per_node: int = 1,
        num_negative_samples: int = 1,
        sparse: bool = False,
        p: float = 1.0,
        q: float = 1.0,
    ):
        super().__init__()
        self.node_embedding = Embedding(num_embeddings, node_embedding_dim, sparse=sparse)
        self.EPS = 1e-15
        assert walk_length >= context_size

        self.node_embedding_dim = node_embedding_dim
        self.walk_length = walk_length - 1
        self.context_size = context_size
        self.walks_per_node = walks_per_node
        self.num_negative_samples = num_negative_samples

        # if WITH_PYG_LIB and p == 1.0 and q == 1.0:
        #     self.random_walk_fn = torch.ops.pyg.random_walk
        # el
        if WITH_TORCH_CLUSTER:
            self.random_walk_fn = torch.ops.torch_cluster.random_walk
        elif p == 1.0 and q == 1.0:
            raise ImportError(f"'{self.__class__.__name__}' "
                                f"requires either the 'pyg-lib' or "
                                f"'torch-cluster' package")
        else:
            raise ImportError(f"'{self.__class__.__name__}' "
                                f"requires the 'torch-cluster' package")

        self.p, self.q = p, q
        self.reset_parameters()

    def reset_parameters(self):
        r"""Resets all learnable parameters of the module."""
        self.node_embedding.reset_parameters()


    def forward(self, walks: Optional[Tensor] = None) -> Tensor:
        """Returns the embeddings for the nodes in :obj:`batch`."""
        emb = self.node_embedding.weight
        return emb if walks is None else emb.index_select(0, walks)


    @torch.jit.export
    def pos_sample(self, start: Tensor, rowptr: Tensor, col: Tensor) -> Tensor:
        '''
        This will return positive samples for each node in the start provided on the batched graph provided

        The number of walks that will be produced per random walk (num_walks_per_rw) performed by this model is 
        the walk length - context size + 1

        The number of positive sample walks produced will be equal to the size of the batch * the number of walks per node * the number of walks per random walk
        '''
        # print('Positive sampling')
        # print('Getting start indices')
        start = start.repeat(self.walks_per_node)
        # print('Random walking')
        # print(f'{len(rowptr)=}, {len(col)=}, {len(start)=}, {self.walk_length=}, {self.p=}, {self.q=}')
        rw = self.random_walk_fn(rowptr, col, start,
                                 self.walk_length, self.p, self.q)
        if not isinstance(rw, Tensor):
            rw = rw[0]

        # print('Getting context size walks')
        walks = rw.unfold(1, self.context_size, 1).reshape(-1, self.context_size)
        return walks

    @torch.jit.export
    def neg_sample(self, start: Tensor, num_nodes: int, device: str) -> Tensor:
        '''
        This will return randomly generated walks where the walk can go to any node between 0 and the number
        of nodes in the batch. 

        The number of walks that will be produced per random walk (num_walks_per_rw) performed by this model is 
        the walk length - context size + 1

        This will return walks per node * the number of requested negative samples * the number of walks per random walk negative random walks for each node in the start tensor provided.
        '''
        # print('Negative sampling')
        start = start.repeat(self.walks_per_node * self.num_negative_samples)

        rw = torch.randint(num_nodes, (start.size(0), self.walk_length), device=device)
        rw = torch.cat([start.view(-1, 1), rw], dim=-1)

        walks = rw.unfold(1, self.context_size, 1).reshape(-1, self.context_size)
        return walks

    @torch.jit.export
    def sample(self, batch: Data, device: str) -> Tuple[Tensor, Tensor]:
        '''
        This will sample positive and negative random walks from the data batch passed in
        '''
        if not isinstance(batch, Data):
            raise TypeError("The batch is of the incorrect type")

        # print('Convert graph to CSR format')
        rowptr, col, _ = batch.csr()
        rowptr, col = rowptr[None], col[None]

        # print('Get start indices')
        start_indices = torch.arange(0, batch.num_nodes, dtype=torch.long).flatten().to(device)
        # print(colored(f'{batch.num_nodes=}, {rowptr.max().item()=}, {col.max().item()=}, {start_indices.max().item()=}\n{rowptr=}\n\n{col=}', 'cyan'))
        return self.pos_sample(start_indices, rowptr, col), self.neg_sample(start_indices, batch.num_nodes, device)

    @torch.jit.export
    def loss(self, data: Data, pos_rw: Tensor, neg_rw: Tensor) -> Tensor:
        r"""Computes the loss given positive and negative random walks."""

        pos_rw_attrs = data.x[pos_rw]
        neg_rw_attrs = data.x[neg_rw]

        # Positive loss.
        start, rest = pos_rw_attrs[:, 0], pos_rw_attrs[:, 1:].contiguous()

        # print()
        # print('start', start.max())
        # # print('rest', rest)
        # print(f'{self.node_embedding.num_embeddings=}')
        # print()

        h_start = self.node_embedding(start).view(pos_rw_attrs.size(0), 1,
                                             self.node_embedding_dim)
        h_rest = self.node_embedding(rest.view(-1)).view(pos_rw_attrs.size(0), -1,
                                                    self.node_embedding_dim)

        out = (h_start * h_rest).sum(dim=-1).view(-1)
        pos_loss = -torch.log(torch.sigmoid(out) + self.EPS).mean()

        # Negative loss.
        start, rest = neg_rw_attrs[:, 0], neg_rw_attrs[:, 1:].contiguous()

        h_start = self.node_embedding(start).view(neg_rw_attrs.size(0), 1,
                                             self.node_embedding_dim)
        h_rest = self.node_embedding(rest.view(-1)).view(neg_rw_attrs.size(0), -1,
                                                    self.node_embedding_dim)

        out = (h_start * h_rest).sum(dim=-1).view(-1)
        neg_loss = -torch.log(1 - torch.sigmoid(out) + self.EPS).mean()

        return pos_loss + neg_loss


    def __repr__(self) -> str:
        return (f'{self.__class__.__name__}({self.node_embedding.weight.size(0)}, '
                f'{self.node_embedding.weight.size(1)})')