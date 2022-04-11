#!/bin/bash
if [[ -f data/Graphs/GraphGenerator/Nodes.csv ]]; then
    rm -rf data/Graphs/
fi

mkdir -p data/Graphs
unzip data_zipped/graphs.zip -d data/Graphs
