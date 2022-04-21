# cfg2vec

This is a project designed to extract control flow graphs and produce embeddings. 

## Setup and Use

All of the following commands assume you are in the top-level directory of the repository unless stated otherwise
1. First, you need to extract the data which you can do by running `extract_data.sh`
2. You can generate the graphs by running `./graphgen`
3. You then need to extract the graphs to the data folder by using `extract_graphs.sh`
4. You can then generate the graph embeddings by running `python embed_graphs.py`
5. You can generate the naive embeddings by running `python naive_vectors.py`
6. You can fetch the generated code2vec embeddings by using `./get_vectors.sh`
7. Fetch all python dependencies for this package by running `pip install requirements.txt`
8. To view our analysis, run `jupyter-lab` and then open the `Analysis.ipynb` and `visualize_vectors.ipynb` in JupyterLab. 
