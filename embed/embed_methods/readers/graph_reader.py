from __future__ import annotations
from graph_tool import load_graph, Graph
from graph_tool.generation import graph_union
from pathlib import Path
from more_itertools import ichunked
import os


def get_graph(graph_path: str) -> Graph:
    program_graph = load_graph(graph_path, fmt='xml')
    return program_graph


class GraphBatcher:
    def __init__(
        self,
        graphs_file_list: list[str],
        graphs_dir: Path,
        batch_size: int,
        ) -> None:
        self.graphs_file_list = graphs_file_list
        self.graphs_dir = graphs_dir
        self.batch_size = batch_size

    def len(self):
        return len(self.graphs_file_list)

    def __iter__(self):

        batched_graph = Graph()
        g_n = 1
        for batch in ichunked(self.graphs_file_list, self.batch_size):
            print(f'Loading batched graph {g_n}')
            for program_graph in (get_graph(s) for s in map(lambda x: str((self.graphs_dir / x).resolve()), batch)):
                graph_union(batched_graph, program_graph, include=True, internal_props=True)

            print(f'Yielding batched graph {g_n}')
            yield batched_graph
            batched_graph.clear()
            g_n += 1


def get_all_graph_files(target_dir: Path, file_pattern: str = '*'):
    yield from (f for f in target_dir.rglob(file_pattern) if f.is_file())


def get_all_graphs(target_dir: Path, file_pattern: str = '*'):
    for graph_file in get_all_graph_files(target_dir, file_pattern):
        graph = load_graph(str(graph_file.resolve()), fmt='xml')
        yield (graph, graph_file)

