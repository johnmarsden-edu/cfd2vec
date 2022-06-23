from pathlib import Path
import networkx as nx
import pandas as pd
import os
import numpy as np
from embed_methods.ge import DeepWalk
from embed_methods.ge import Node2Vec

def get_models(strategy, graph):
  return [
    (
      f'{strategy}deepwalk.csv',
      DeepWalk(
        graph, 
        walk_length=10, 
        num_walks=20, 
        workers=1, 
        get_node_data=True
      )
    ),
    (
      f'{strategy}node2vec.csv',
      Node2Vec(
        graph,
        walk_length=10,
        num_walks=20,
        p=1.5,
        q=1.5,
        workers=1,
        get_node_data=True
      )
    )
  ]


def train_models(strategy: str, embed_size: int):
  node_file_name = f'{strategy}Nodes.csv'
  edge_file_name = f'{strategy}Edges.csv'
  graph_dir = Path.cwd() / 'data' / 'Graphs'
  nodes = pd.read_csv(graph_dir / node_file_name)
  edges = pd.read_csv(graph_dir / edge_file_name)
  graph = load_graphs(nodes, edges)
  models = get_models(strategy, graph)

  for filename, model in models:
    model.train(window_size=5, iter=3, embed_size=embed_size)
    embeddings = model.get_embeddings()
    embed_data = []
    ids = []
    code_id = ''
    for row in nodes.itertuples():
      if row.CodeStateId != code_id:
        embed_data.append(np.array(embeddings[row.NodeData]))
        code_id = row.CodeStateId
        ids.append(code_id)
      else:
        embed_data[-1] += embeddings[row.NodeData]
    for i in range(len(embed_data)):
      embed_data[i] = embed_data[i] / np.linalg.norm(embed_data[i])
    out_csv = pd.DataFrame(embed_data)
    out_csv['CodeStateID'] = ids
    out_csv.to_csv(os.path.join('data', 'Vectors', filename), index=False)
 
def main():
  EMBED_SIZE = 50
  train_models('fullCanonicalization', EMBED_SIZE)
  train_models('partCanonicalization', EMBED_SIZE)
  train_models('noneCanonicalization', EMBED_SIZE)
  
def load_graphs(nodes, edges):
  graph = nx.DiGraph()
  for row in nodes.itertuples():
    graph.add_node(f'{row.CodeStateId}_{row.MethodNum}_{row.NodeId}', data=row.NodeData)
  
  for row in edges.itertuples():
    graph.add_edge(f'{row.CodeStateId}_{row.MethodNum}_{row.Node1Id}', 
                   f'{row.CodeStateId}_{row.MethodNum}_{row.Node2Id}', data=row.EdgeData)
  
  return graph

if __name__ == "__main__":
  main()
