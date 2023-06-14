from pathlib import Path
from gnn.graph_reader import get_all_graphs
from typing import TextIO
# Constants
GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs' / 'raw'
STRATS = ['NC', 'LC', 'VC', 'FC']


def write_vocab_word(vocab_file: TextIO, word: str):
    vocab_file.write('"')
    vocab_file.write(word)
    vocab_file.write('"')
    vocab_file.write('\n')


# create the iterable
## create node and edge vocab
for strat in STRATS:
    with open(f'vocab_lists/{strat}_graph_name_vocab_list.txt', 'w') as graph_name_vocab_list:
        for index, (graph, graph_path) in enumerate(get_all_graphs(GRAPH_DIR, f'{strat}-*/**/*')):
            if index % 100000 == 0:
                print(f'We have procesed {index} graphs to create the graph name vocab for {strat} canonicalization strategy')

            graph_name = list(name[:-7] for name in graph.vp['code'] if name.endswith('source') and not name.startswith('anonymous'))
            if len(graph_name) == 0:
                print(f'There was no source node in {graph_path}')
            elif len(graph_name) > 1:
                print(f'There was more than one source node in {graph_path}')
            else:
                write_vocab_word(graph_name_vocab_list, graph_name[0])
            