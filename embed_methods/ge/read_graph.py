import pandas as pd
import networkx as nx

  def read_graph(filename):
    node_csv = pd.read_csv(filename + '_nodes.csv')
    graph = nx.DiGraph()
    for row in node_csv.itertuples():
      graph.add_node(node_csv['nodeId'], data=node_csv['nodeData'])
    edge_csv = pd.read_csv(filename + '_edges.csv')
    for row in node_csv.itertuples():
      graph.add_edge(edge_csv['node1Id'], edge_csv['node2Id'], data=node_csv['edgeData'])
    return graph