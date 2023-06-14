from pathlib import Path
import pandas as pd
import pickle
import numpy as np

 

# Load final exam and assignment data
data = pd.DataFrame(columns=['SubjectID', 'Assignment', 'CodeStateID'])
grades = {}
scores = {}
total_non_compile = 0
data_dir = Path.cwd() / '..' / 'data'
for directory in [data_dir / 'F19_All' / 'Train', data_dir / 'S19_All']:
    grade_data = pd.read_csv(directory / 'Data' / 'LinkTables' / 'Subject.csv')
    for row in grade_data.itertuples():
        grades[row.SubjectID] = row._2 if directory.name == 'S19_All' else row._2 / 100
    main = pd.read_csv(directory / 'Data' / 'MainTable.csv')
    total_non_compile += len(main['CodeStateID'].unique())
    for row in main[main['EventType'] == 'Run.Program'].itertuples():
        scores[row.CodeStateID] = row.Score
    main['Assignment'] = [f'{int(x[0])}-{int(x[1])}' for x in list(zip(main['AssignmentID'], main['ProblemID']))]
    data = pd.concat([data, main[['SubjectID', 'Assignment', 'CodeStateID']]], ignore_index=True)

data['Score'] = [scores[x] for x in data['CodeStateID']]
data['ExamGrade'] = [grades[x] if x in grades else 0 for x in data['SubjectID']]

print(f'ALL Programs: {total_non_compile}')   

# Find the Code IDs for the submissions with the highest score for each student for each assignment
students = list(data['SubjectID'].unique())
assignments = data['Assignment'].unique()
best_score_data = {}
num_null_submissions = 0
for student in students:
    best_ids = [None] * len(assignments)
    s_filter = data[data['SubjectID'] == student]
    a_idx = 0
    for assignment in assignments:
        a_filter = s_filter[s_filter['Assignment'] == assignment].reset_index()
        if len(a_filter.index) > 0:
            best_ids[a_idx] = a_filter['CodeStateID'][np.argmax(a_filter['Score'])]
        else:
            num_null_submissions += 1
        a_idx += 1
    best_score_data[student] = best_ids

print(f"TOTAL Number of submissions: {len(data.index)}")

print(f"Number of Students: {len(students)}")
print(f"Number of student-assignment pairs: {len(students) * len(assignments)}")
print(f"Number of assignment-student pairs with no submissions: {num_null_submissions}")

def my_dump(obj, filename):
    with open(filename, 'wb') as pickle_file:
        print(type(obj))
        pickle.dump(obj, pickle_file)

my_dump(students, 'student_metadata/students.pkl')
my_dump(grades, 'student_metadata/grades.pkl')
my_dump(best_score_data, 'student_metadata/best_score_data.pkl')