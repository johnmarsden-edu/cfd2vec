import random
from pathlib import Path
import csv
import random

data_dir = Path.cwd() / '..' / 'data'
c2v_gen_dir = Path.cwd() / 'code2vec'

train_dir = c2v_gen_dir / 'Train'
test_dir = c2v_gen_dir / 'Test'
val_dir = c2v_gen_dir / 'Validate'

train_dir.mkdir(parents=True)
test_dir.mkdir(parents=True)
val_dir.mkdir(parents=True)

code_ids = []


def get_base_dir(splitter: float):
    if splitter < .2:
        return test_dir
    elif splitter < .4:
        return val_dir

    return train_dir


for directory in [data_dir / 'F19_All' / 'Train', data_dir / 'F19_All' / 'Test', data_dir / 'S19_All']:
    path = directory / 'Data' / 'CodeStates' / 'CodeStates.csv'
    with open(path, 'r') as file:
        reader = csv.DictReader(file)
        for row in reader:
            id = row['CodeStateID']
            code = row['Code']
            splitter = random.random()
            with open(get_base_dir(splitter) / f'{id}.java', 'w') as code_file:
                code_file.write(code)