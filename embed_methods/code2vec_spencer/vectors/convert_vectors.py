import pandas as pd

def convert_vectors(raw_paths, id_paths, save_path):
  vectors = []
  code_ids = []
  for path in raw_paths:
    with open(path, 'r') as f:
      for line in f:
        vectors.append([float(n) for n in line.rstrip().split(' ')])
  for path in id_paths:
    with open(path, 'r') as f:
      for line in f:
        id = line.strip()
        code_ids.append(id)
  data = pd.DataFrame(vectors)
  data['CodeStateID'] = code_ids
  data = data[data['CodeStateID'] != '']
  data.to_csv(save_path)
  
convert_vectors(['saved_model_iter8_train', 'saved_model_iter8_val', 'saved_model_iter8_test'], ['../code2vec/data/all_dataset/with_ids/all_dataset.train.c2v',
                                                 '../code2vec/data/all_dataset/with_ids/all_dataset.val.c2v',
                                                 '../code2vec/data/all_dataset/with_ids/all_dataset.test.c2v'], 'pt_nt_all.csv')

# convert_vectors(['saved_model_iter8_train', 'saved_model_iter8_val', 'saved_model_iter8_test'], ['../code2vec/data/my_dataset/with_ids/my_dataset.train.c2v',
                                                 # '../code2vec/data/my_dataset/with_ids/my_dataset.val.c2v',
                                                 # '../code2vec/data/my_dataset/with_ids/my_dataset.test.c2v'], 'untuned_vectors.csv')

# convert_vectors(['saved_model_iter19_train', 'saved_model_iter19_val', 'saved_model_iter19_test'], ['../code2vec/data/my_dataset/with_ids/my_dataset.train.c2v',
                                                 # '../code2vec/data/my_dataset/with_ids/my_dataset.val.c2v',
                                                 # '../code2vec/data/my_dataset/with_ids/my_dataset.test.c2v'], 'custom_vectors_2.csv')
