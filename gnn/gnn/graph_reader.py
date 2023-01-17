# create an iterable class 
# that will iterate over all the 
# graphml documents in a directory
# load them into a pyg graph
# and return it one graph at a time.
# it might also be all of the graphs
# in a particular program so be sure 
# to handle that case
from graph_tool import load_graph
from torch import Tensor
from torch_geometric.data import Data, Dataset
from pathlib import Path
from typing import Callable, Optional
import torch
from torchtext.vocab import Vocab

class GraphMLDataset(Dataset):
    def __init__(
        self,
        save_dir: Path,
        node_vocab: Vocab, 
        edge_vocab: Vocab,
        graph_name_vocab: Vocab,
        commits_file_list: list[str],
        commits_dir: Path,
        commit_transform: Optional[Callable[[Data], Data]] = None,
        ) -> None:

        super().__init__(str(save_dir.resolve()))
        self.node_vocab = node_vocab
        self.edge_vocab = edge_vocab
        self.graph_name_vocab = graph_name_vocab
        self.commits_file_list = commits_file_list
        self.commits_dir = commits_dir
        self.commit_transform = commit_transform

    def len(self):
        return len(self.commits_file_list)

    
    def __transform_code(self, code: str) -> str:
        if code.endswith('source'):
            self.graph_name = code[:-7]
            return 'anonymous source'
        elif code.endswith('sink'):
            return 'anonymous sink'

        return code

    def get(self, idx: int) -> Data:
        commit_file_id: str = self.commits_file_list[idx]
        graph_path: Path = self.commits_dir / commit_file_id
        program_graph = load_graph(str(graph_path.resolve()), fmt='xml')
        self.graph_name = None
        code_ids: list[int] = [self.node_vocab[self.__transform_code(code)] for code in program_graph.vp['code']] if 'code' in program_graph.vp else []

        node_features: Tensor = torch.tensor(code_ids)

        edges: Tensor = torch.tensor(program_graph.get_edges())

        edge_transfer_ids: list[int] = [self.edge_vocab[transfer] for transfer in program_graph.ep['transfer']] if 'transfer' in program_graph.ep else []
        edge_features: Tensor = torch.tensor(edge_transfer_ids, dtype=torch.int32)
        graph_data: Data = Data(x=node_features, edge_index=edges.t().contiguous(), edge_attr=edge_features)
        if self.commit_transform:
            graph_data = self.commit_transform(graph_data)
        
        self.graph_name = self.graph_name or 'anonymous'
        graph_data.graph_name_index = self.graph_name_vocab[self.graph_name]
        
        assert graph_data.graph_name_index < len(self.graph_name_vocab)

        graph_data.validate(raise_on_error=True)
        return graph_data


def get_all_graph_files(target_dir: Path, file_pattern: str = '*'):
    yield from (f for f in target_dir.rglob(file_pattern) if f.is_file())


def get_all_graphs(target_dir: Path, file_pattern: str = '*'):
    for graph_file in get_all_graph_files(target_dir, file_pattern):
        graph = load_graph(str(graph_file.resolve()), fmt='xml')
        yield (graph, graph_file)

