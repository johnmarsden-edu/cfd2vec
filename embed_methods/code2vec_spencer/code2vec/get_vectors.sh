for model in 'java14m_trainable/saved_model_iter8'; do #'test_model_2/saved_model_iter19' 'tuned_model/tuned_model_iter1'; do
  for path in 'train' 'val' 'test'; do
    full_file='data/all_dataset/all_dataset.'$path'.c2v'
    python code2vec.py --load 'models/'$model --test  $full_file --export_code_vectors
    mv "$full_file".vectors ../vectors/"$(basename $model)_$path"
  done
done
