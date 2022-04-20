import os
import numpy as np
import pandas as pd

from sklearn.feature_extraction.text import TfidfVectorizer

def main():
  code_vectorizer = TfidfVectorizer(max_features=50)
  programs = []
  code_ids = []
  for directory in ['F19_All', 'S19_All']:
    path = os.path.join('data', directory, 'Train' if directory == 'F19_All' else '', 'Data', 'CodeStates', 'CodeStates.csv')
    frame = pd.read_csv(path)
    frame = frame[[x is not np.nan for x in frame['Code']]].reset_index()
    programs += list(frame['Code'])
    code_ids += list(frame['CodeStateID'])
  code_vectorizer.fit(programs)
  programs = code_vectorizer.transform(programs)
  out_frame = pd.DataFrame(programs.todense())
  out_frame['CodeStateID'] = code_ids
  out_frame.to_csv(os.path.join('data', 'Vectors', 'tf_idf.csv'))

if __name__ == "__main__":
  main()
