import enum
import gc
import pprint
from dataclasses import dataclass
from enum import Enum, auto
from pathlib import Path
from typing import Optional

import networkx as nx
import numpy as np
import pandas as pd
import psycopg as db
from dotenv import load_dotenv
from networkx import Graph
from psycopg import Connection
from psycopg.rows import class_row
from psycopg.types.enum import EnumInfo, register_enum

from embed_methods.ge import DeepWalk
from embed_methods.ge import Node2Vec

load_dotenv()

EMBED_SIZE = 50


# noinspection PyArgumentList
class NodeType(Enum):
    Source = auto()
    Sink = auto()
    Statement = auto()
    Control = auto()
    Decision = auto()
    Exception = auto()


# noinspection PyArgumentList
class EdgeType(Enum):
    Statement = auto()
    Decision = auto()
    Exception = auto()


@dataclass
class ProgramNode:
    program_id: str
    graph_id: str
    node_type: NodeType
    label: Optional[str] = None
    contents: Optional[str] = None

    def __str__(self) -> str:
        return node_data_string(self.node_type, self.label, self.contents)


def node_data_string(node_type: NodeType, label: Optional[str], contents: Optional[str]):
    match node_type:
        case NodeType.Source:
            return f'Source {contents}'
        case NodeType.Sink:
            return f'Sink {contents}'

    out_label = ''
    if label:
        out_label = f'{label}: '

    out_contents = ''
    if contents:
        out_contents = contents
    return f'{out_label}{out_contents}'


@dataclass
class Node:
    id: int
    node_type: NodeType
    label: Optional[str] = None
    contents: Optional[str] = None

    def __str__(self) -> str:
        return node_data_string(self.node_type, self.label, self.contents)


@dataclass
class Edge:
    source: int
    target: int
    edge_type: EdgeType
    direction: Optional[bool] = None
    exception: Optional[str] = None

    def __str__(self) -> str:
        if self.edge_type == EdgeType.Exception:
            return f'{self.exception}'
        elif self.edge_type == EdgeType.Decision:
            return 'True' if self.direction else 'False'
        return ''


def get_models(strat: str, graph: Graph):
    yield (
            f"{strat}-deepwalk.model",
            DeepWalk(
                graph, walk_length=10, num_walks=20, workers=3, get_node_data=True
            ),
        )
    yield (
            f"{strat}-node2vec.model",
            Node2Vec(
                graph,
                walk_length=10,
                num_walks=20,
                p=1.5,
                q=1.5,
                workers=3,
                get_node_data=True,
            ),
        )


def train_model(strat: str, conn: Connection, embed_size: int, vector_dir: Path):
    graph = load_graphs(strat, conn)

    for filename, model in get_models(strat, graph):
        model.train(window_size=5, epochs=5, embed_size=embed_size, min_count=1, workers=3, hs=1, negative=0).save(str(vector_dir / filename))
        del model
        gc.collect()


STRATS = ['NC', 'VC', 'LC', 'FC']


def train_models(embed_size: int):
    data_dir = Path.cwd() / "data"
    vector_dir = data_dir / "Vectors"
    vector_dir.mkdir(parents=True, exist_ok=True)
    with db.connect() as conn:
        edge_type_info = EnumInfo.fetch(conn, 'edge_type')
        register_enum(edge_type_info, conn, EdgeType)
        node_type_info = EnumInfo.fetch(conn, 'node_type')
        register_enum(node_type_info, conn, NodeType)

        for strat in STRATS:
            train_model(strat, conn, embed_size, vector_dir)


def main():
    train_models(EMBED_SIZE)


def load_graphs(strat: str, conn: Connection):
    graph = nx.MultiDiGraph()
    with conn.cursor(row_factory=class_row(Node)) as cursor:
        cursor.execute('''
SELECT
    n.id,
    node_type,
    label,
    contents
FROM
    nodes n
JOIN
    graphs g on g.id = n.graph_id
JOIN
    programs p on g.program_id = p.id
WHERE
    p.program_id LIKE %s
''', (strat + '%',))
        for row in cursor.fetchall():
            graph.add_node(row.id, data=str(row))

    with conn.cursor(row_factory=class_row(Edge)) as cursor:
        cursor.execute('''
SELECT 
    source, 
    target, 
    edge_type, 
    direction, 
    exception 
FROM 
    edges e
JOIN
    graphs g on g.id = e.graph_id
JOIN
    programs p on g.program_id = p.id
WHERE
    p.program_id LIKE %s
''', (strat + '%',))
        edges = cursor.fetchall()
        for edge in edges:
            graph.add_edge(
                edge.source,
                edge.target,
                data=str(edge)
            )

    print("Graph to train on: ", graph)
    return graph


if __name__ == "__main__":
    main()

# %%
