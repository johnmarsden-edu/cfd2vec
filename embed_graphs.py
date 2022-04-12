import networkx as nx
import pandas as pd
import os

def main():
  nodes = pd.read_csv(os.path.join('data', 'Graphs', 'Nodes.csv'))
  edges = pd.read_csv(os.path.join('data', 'Graphs', 'Edges.csv'))
  graphs = {}

if __name__ == "__main__":
  main()