from __future__ import annotations  
from csv import DictReader, DictWriter
from pathlib import Path
import sys
import csv
import random

csv.field_size_limit(sys.maxsize)


def write_files_to_output(output: DictWriter[str], files: dict[str, list[dict[str, str]]]):
    previous_index: dict[str, int] = {}
    index = 1
    while len(files) > 0:
        repo_file_name, graphs = random.choice(list(files.items()))
        commit_data = graphs.pop(0)


        new_row: dict[str, str | int] = {
            'row': index,
            'name': f'{repo_file_name} {commit_data["commitid"]}', 
            'body': commit_data['data']
        }

        if repo_file_name in previous_index:
            new_row['previous'] = previous_index[repo_file_name]
        
        output.writerow(new_row)

        if len(graphs) == 0:
            del files[repo_file_name]

        previous_index[repo_file_name] = index
        index += 1

# columns 
# commitid,repo_name,assignment,file,data
repo_data_folder = Path.cwd() / 'repo_data'

print('--------------------------------------------------------------------------------')
for assignment_data_file in repo_data_folder.glob('*.csv'):
    with open(assignment_data_file, 'r') as adf, open(assignment_data_file.with_name(f'{assignment_data_file.stem}_taguette').with_suffix('.csv'), 'w') as odf, open(assignment_data_file.with_name(f'{assignment_data_file.stem}_taguette_validation').with_suffix('.csv'), 'w') as vodf:
        print(f'Processing {assignment_data_file.name}')
        assignment_data = DictReader(adf)
        validation_output_csv = DictWriter(vodf, fieldnames=['row', 'name', 'body', 'previous'])
        output_csv = DictWriter(odf, fieldnames=['row', 'name', 'body', 'previous'])

        validation_output_csv.writeheader()
        output_csv.writeheader()

        files: dict[str, list[dict[str, str]]] = {}
        current_prevs = {}
        prev_bodies: dict[str, set[str]] = {}
        total_file_graphs = 0
        for row_idx, commit_data in enumerate(list(assignment_data), 1):
            repo_file_name = f'{commit_data["repo_name"]} {commit_data["file"]}'
            commit_data['provided'] = commit_data['provided'] == 'True'
            commit_data['changed'] = commit_data['changed'] == 'True'
            if commit_data['provided']:
                continue

            if not commit_data['changed']:
                continue

            if 'Test' in commit_data['file']:
                continue

            if repo_file_name not in files:
                files[repo_file_name] = []

            files[repo_file_name].append(commit_data)
            total_file_graphs += 1

        print(f'It contains {total_file_graphs} file graphs')
        for key, value in files.items():
            duped: set[str] = set()
            deduped: list[dict[str, str]] = []
            for commit in value:
                if commit['data'] in duped:
                    continue

                duped.add(commit['data'])
                deduped.append(commit)

            files[key] = deduped

        
        
        dd_total_file_graphs = sum(len(v) for v in files.values())
        desired_file_graphs = dd_total_file_graphs / 10
        total_validation_graphs = 0
        validation_files = {}
        while total_validation_graphs < desired_file_graphs:
            repo_file_name, graphs = random.choice(list(files.items()))
            validation_files[repo_file_name] = graphs
            del files[repo_file_name]
            total_validation_graphs += len(graphs)

        print(f'Creating validation taguette file for {assignment_data_file.name}')
        write_files_to_output(validation_output_csv, validation_files)

        print(f'Creating taguette file for {assignment_data_file.name}')
        write_files_to_output(output_csv, files)

        print(f'{assignment_data_file.name} has {total_file_graphs} total file graphs which after deduping is {dd_total_file_graphs}')

    print('--------------------------------------------------------------------------------')