import networkx as nx
import pandas as pd
import os
import numpy as np
from embed_methods.ge import DeepWalk
from embed_methods.ge import Node2Vec

def main():
  EMBED_SIZE = 50
  graph = load_graphs()
  models = [
    # ('deepwalk.csv', DeepWalk(graph, walk_length=10, num_walks=20, workers=1, get_node_data=True)),
    ('node2vec.csv', Node2Vec(graph, walk_length=10, num_walks=20, p=1.5, q=1.5, workers=1, get_node_data=True))
  ]
  
  for filename, model in models:
    model.train(window_size=5, iter=3, embed_size=EMBED_SIZE)
    embeddings = model.get_embeddings()
    embed_data = []
    ids = []
    nodes = pd.read_csv(os.path.join('data', 'Graphs', 'Nodes.csv'))
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
  
def load_graphs():
  nodes = pd.read_csv(os.path.join('data', 'Graphs', 'Nodes.csv'))
  edges = pd.read_csv(os.path.join('data', 'Graphs', 'Edges.csv'))
  graph = nx.DiGraph()
  for row in nodes.itertuples():
    graph.add_node(f'{row.CodeStateId}_{row.MethodNum}_{row.NodeId}', data=row.NodeData)
  
  for row in edges.itertuples():
    graph.add_edge(f'{row.CodeStateId}_{row.MethodNum}_{row.Node1Id}', 
                   f'{row.CodeStateId}_{row.MethodNum}_{row.Node2Id}', data=row.EdgeData)
  
  return graph

if __name__ == "__main__":
  main()