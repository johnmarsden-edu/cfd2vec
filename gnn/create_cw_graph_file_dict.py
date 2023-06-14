from pathlib import Path
from pickle import dump
# Constants
GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs' / 'raw'
cw_graph_file_dict: dict[str, dict[str, list[str]]] = {
    'NC': {}, 'LC': {}, 'VC': {}, 'FC': {}
}

for strat in cw_graph_file_dict.keys():
    codestate_dir = GRAPH_DIR / f'{strat}-CodeStates'
    for codestate in codestate_dir.iterdir():
        cw_graph_file_dict[strat][codestate.name] = []
        for file in codestate.iterdir():
            cw_graph_file_dict[strat][codestate.name].append(str(file.relative_to(GRAPH_DIR)))

with open(f'graphs_metadata/cw_graphs_file_dict.pkl', 'wb') as cw_graph_file_dict_pickle:
    dump(cw_graph_file_dict, cw_graph_file_dict_pickle)