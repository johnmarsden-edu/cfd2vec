from pathlib import Path
from lxml import etree
from gnn.graph_reader import get_all_graph_files
from copy import deepcopy

GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs' / 'raw'

for num, graph in enumerate(get_all_graph_files(GRAPH_DIR)):
    if num % 10000 == 0:
        print(f'We have fixed {num} graphs so far')
    filename = str(graph.resolve())
    graph_xml = etree.parse(filename)
    graphml = graph_xml.getroot()

    graphml.insert(0, deepcopy(graphml[-1]))
    graphml.insert(0, deepcopy(graphml[-2]))

    graphml.remove(graphml[-1])
    graphml.remove(graphml[-1])
    
    graph_xml.write(filename, pretty_print=True)