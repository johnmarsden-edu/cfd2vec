# create an iterable class 
# that will iterate over all the 
# graphml documents in a directory
# load them into a pyg graph
# and return it one graph at a time.
# it might also be all of the graphs
# in a particular program so be sure 
# to handle that case
from __future__ import annotations
from typing import Callable, Optional, Union, Any, Sequence
from graph_tool import load_graph
from pathlib import Path, PosixPath
import os.path as osp
import torch
from torch import Tensor
from torchtext.vocab import Vocab
from torch_geometric.data import Data, Dataset, Batch

def get_processed_file_name(commit_file: str) -> str:
    return str(Path(commit_file).with_suffix('.pt'))


def to_list(value: Any) -> Sequence:
    if isinstance(value, Sequence) and not isinstance(value, str):
        return value
    else:
        return [value]

class CodeWorkoutDataset(Dataset):
    def __init__(
        self,
        save_dir: Path,
        best_score_data: Any,
        strat: str,
        ) -> None:

        self.codestate_dirs = [f'{strat}-CodeStates/{codestate}/' for student in best_score_data.values() for codestate in student if codestate]
        self.processed_files_list = [
            f'{strat}-CodeStates/{codestate}.pth' for student in best_score_data.values() for codestate in student if codestate
        ]

        super().__init__(root=str(save_dir.resolve()))

        self.processed_dir_path = PosixPath(self.processed_dir)

    @property
    def raw_dir(self) -> str:
        return osp.join(self.root, 'processed')
    
    @property
    def processed_dir(self) -> str:
        return osp.join(self.root, 'combined')
    
    @property
    def processed_file_names(self) -> Union[str, list[str], tuple[str, str]]:
        return self.processed_files_list
    
    def process(self):
        raw_dir = PosixPath(self.raw_dir)
        processed_dir = PosixPath(self.processed_dir)

        for (codestate_dir, codestate_graph) in zip(self.codestate_dirs, self.processed_files_list):
            codestate_path = raw_dir / codestate_dir
            processed_codestate_path = processed_dir / codestate_graph

            if not codestate_path.exists():
                torch.save(None, str(processed_codestate_path))
                continue

            codestate_graphs = [
                torch.load(graph_path.resolve()) for graph_path in codestate_path.iterdir()
            ]

            processed_codestate = Batch.from_data_list(codestate_graphs)
            processed_codestate_path.parent.mkdir(parents=True, exist_ok=True)
            torch.save(processed_codestate, str(processed_codestate_path))

    def len(self):
        return len(self.processed_files_list)
    
    def get(self, idx: int) -> Data:
        target = self.processed_files_list[idx]
        path_to_target = self.processed_dir_path / target
        return path_to_target.stem, torch.load(path_to_target.resolve())



class PytorchDataset(Dataset):
    def __init__(
        self,
        save_dir: Path,
        ) -> None:
        self.save_dir = save_dir
        self.file_list = list(save_dir.glob('*.pt'))

        # Initializing the root
        super().__init__(root=str(save_dir.resolve()))

    def len(self):
        return len(self.file_list)

    def get(self, idx: int):
        target = self.file_list[idx]
        return torch.load(target.resolve())

    


class GraphToolDataset(Dataset):
    def __init__(
        self,
        save_dir: Path,
        node_vocab: Vocab, 
        edge_vocab: Vocab,
        graph_name_vocab: Vocab,
        graphs_file_list: list[str],
        commit_transform: Optional[Callable[[Data], Data]] = None,
        ) -> None:

        # Before initializing the root
        self.node_vocab: Vocab = node_vocab
        print(f'{len(self.node_vocab)=}')
        self.edge_vocab: Vocab = edge_vocab
        self.graph_name_vocab: Vocab = graph_name_vocab
        self.graphs_file_list: list[str] = graphs_file_list
        self.processed_files_list: list[str] = list(map(get_processed_file_name, graphs_file_list))
        self.commit_transform = commit_transform

        # Initializing the root
        super().__init__(root=str(save_dir.resolve()))

        # After initializing the root.
        self.processed_dir_path = PosixPath(self.processed_dir)

    # @property
    # def processed_file_names(self) -> Union[str, list[str], tuple[str, str]]:
    #     return self.processed_files_list

    
    # def process(self):
    #     raw_dir = PosixPath(self.raw_dir)
    #     processed_dir = PosixPath(self.processed_dir)
    #     for (commit_file, processed_commit_file) in zip(self.graphs_file_list, self.processed_files_list):
    #         graph_path: Path = raw_dir / commit_file
    #         program_graph = load_graph(str(graph_path.resolve()), fmt='gt')
    #         self.graph_name = None
    #         code_ids: list[int] = [self.node_vocab[self.__transform_code(code)] for code in program_graph.vp['code']] if 'code' in program_graph.vp else []

    #         node_features: Tensor = torch.tensor(code_ids)

    #         edges: Tensor = torch.tensor(program_graph.get_edges())

    #         edge_transfer_ids: list[int] = [self.edge_vocab[transfer] for transfer in program_graph.ep['transfer']] if 'transfer' in program_graph.ep else []
    #         edge_features: Tensor = torch.tensor(edge_transfer_ids, dtype=torch.int32)
    #         graph_data: Data = Data(x=node_features, edge_index=edges.t().contiguous(), edge_attr=edge_features)
    #         graph_data.num_nodes = program_graph.num_vertices()
    #         if self.commit_transform:
    #             graph_data = self.commit_transform(graph_data)
            
    #         self.graph_name = self.graph_name or 'anonymous'
    #         graph_data.graph_name_index = self.graph_name_vocab[self.graph_name]
            
    #         assert graph_data.graph_name_index < len(self.graph_name_vocab)

    #         graph_data.validate(raise_on_error=True)
    #         processed_commit_save_loc = (processed_dir / processed_commit_file).resolve(strict=False)

    #         processed_commit_save_loc.parent.mkdir(parents=True, exist_ok=True)
    #         torch.save(graph_data, str(processed_commit_save_loc))



    def len(self):
        return len(self.processed_files_list)

    
    def __transform_code(self, code: str) -> str:
        if code.endswith('source'):
            self.graph_name = code[:-7]
            return 'anonymous source'
        elif code.endswith('sink'):
            return 'anonymous sink'

        return code

    def get(self, idx: int) -> Data:
        target = self.processed_files_list[idx]
        path_to_target = self.processed_dir_path / target
        return torch.load(path_to_target.resolve())


def get_all_graph_files(target_dir: Path, file_pattern: str = '*'):
    yield from (f for f in target_dir.rglob(file_pattern) if f.is_file())


def get_all_graphs(target_dir: Path, file_pattern: str = '*'):
    for graph_file in get_all_graph_files(target_dir, file_pattern):
        graph = load_graph(str(graph_file.resolve()), fmt='gt')
        yield (graph, graph_file)

