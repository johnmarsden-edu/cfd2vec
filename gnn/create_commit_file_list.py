from pathlib import Path
from gnn.graph_reader import get_all_graph_files
from pickle import dump
# Constants
GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs'
STRATS = ['NC', 'LC', 'VC', 'FC']



# create the iterable
## create node and edge vocab
for strat in STRATS:
    commit_file_list: list[str] = []
    for index, graph_path in enumerate(get_all_graph_files(GRAPH_DIR, f'{strat}-*/**/*')):
        if index % 100000 == 0:
            print(f'We have procesed {index} graph files to create the commit file list pickle for {strat} canonicalization strategy')
        commit_file_list.append(str(graph_path.relative_to(GRAPH_DIR)))

    with open(f'commits_metadata/{strat}_commits_files_list.pkl', 'wb') as commits_files_list_pickle:
        dump(commit_file_list, commits_files_list_pickle)