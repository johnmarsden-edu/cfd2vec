from __future__ import annotations
from typing import Any, TYPE_CHECKING
from graph_tool.all import Graph
import numpy as np

def preprocess_graph(graph: Graph):
    vertex2idx: dict[Any, int] = {}
    idx2vertex: list[Any] = []
    for idx, vertex in enumerate(graph.vertices()):
        vertex2idx[vertex] = idx
        idx2vertex.append(vertex)

    return idx2vertex, vertex2idx

int_array = None if not TYPE_CHECKING else np.ndarray[Any, np.dtype[np.int32]]

def partition_np_array(vertices: int_array, num_workers: int) -> list[int_array]:
    return np.array_split(vertices, num_workers, 0)


def partition_dict(vertices, workers):
    batch_size = (len(vertices) - 1) // workers + 1
    part_list = []
    part = []
    count = 0
    for v1, nbs in vertices.items():
        part.append((v1, nbs))
        count += 1
        if count % batch_size == 0:
            part_list.append(part)
            part = []
    if len(part) > 0:
        part_list.append(part)
    return part_list


def partition_list(vertices: list[int], workers: int) -> list[list[int]]:
    num_vertices: int = len(vertices)
    batch_size: int = (num_vertices - 1) // workers + 1
    part_list: list[list[int]] = []
    prev: int = 0
    for cur in range(batch_size, num_vertices + batch_size, batch_size):
        if cur > num_vertices:
            part_list.append(vertices[prev:num_vertices])
        else:
            part_list.append(vertices[prev:cur])
        prev = cur
    return part_list


def partition_num(num: int, workers: int) -> list[int]:
    if num % workers == 0:
        return [num // workers] * workers
    else:
        return [num // workers] * (workers - 1) + [(num // workers) + (num % workers)]
