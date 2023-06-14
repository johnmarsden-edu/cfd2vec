from torch_geometric.data import Data
from torch_geometric.utils import to_networkx
from networkx.drawing.nx_agraph import write_dot
import torch
new_node_ids = [x for x in range(7)]
sources = [
    0, 1, 2, 2, 4, 5,
]

targets = [
    1, 2, 3, 4, 5, 2,
]

data = Data(torch.tensor(new_node_ids), torch.tensor([sources, targets]))
data.num_nodes = 7

write_dot(to_networkx(data), 'test_test.dot')

device = 'cpu'
rowptr, col, perm = data.to(device).csr()
rowptr, col = rowptr[None], col[None]

print(rowptr, col)
start_indices = torch.arange(0, data.num_nodes, dtype=torch.long).flatten().to(device)

print(torch.ops.torch_cluster.random_walk(rowptr, col, start_indices,
                                 10, 2, 4))
