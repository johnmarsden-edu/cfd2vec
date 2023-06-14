from pathlib import Path
from gnn.graph_reader import get_all_graph_files
from pickle import dump
# Constants
GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs' / 'raw'
STRATS = ['NC', 'LC', 'VC', 'FC']



# create the iterable
for strat in STRATS:
    graph_file_list: list[str] = []
    for index, graph_path in enumerate(get_all_graph_files(GRAPH_DIR, f'{strat}-*/**/*')):
        if index % 100000 == 0:
            print(f'We have procesed {index} graph files to create the graph file list pickle for {strat} canonicalization strategy')
        graph_file_list.append(str(graph_path.relative_to(GRAPH_DIR)))

    with open(f'graphs_metadata/{strat}_graphs_files_list.pkl', 'wb') as graphs_files_list_pickle:
        dump(graph_file_list, graphs_files_list_pickle)