from pathlib import Path
from gnn.graph_reader import get_all_graphs
from typing import TextIO
# Constants
GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs'
STRATS = ['NC', 'LC', 'VC', 'FC']


def write_vocab_word(vocab_file: TextIO, word: str):
    vocab_file.write('"')
    vocab_file.write(word)
    vocab_file.write('"')
    vocab_file.write('\n')


# create the iterable
## create node and edge vocab
for strat in STRATS:
    with open(f'vocab_lists/{strat}_node_vocab_list.txt', 'w') as node_vocab_list, open(f'vocab_lists/{strat}_edge_vocab_list.txt', 'w') as edge_vocab_list:
        for index, (graph, graph_path) in enumerate(get_all_graphs(GRAPH_DIR, f'{strat}-*/**/*')):
            if index % 100000 == 0:
                print(f'We have procesed {index} graphs to create the vocab for {strat} canonicalization strategy')

            if 'code' in graph.vp:
                for code in graph.vp['code']:
                    write_vocab_word(node_vocab_list, code)
                
            if 'transfer' in graph.ep:
                for transfer in graph.ep['transfer']:
                    write_vocab_word(edge_vocab_list, transfer)

