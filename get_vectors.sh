#!/bin/bash



if [[ -f data_zipped/vectors.zip ]]; then
    rm data_zipped/vectors.zip;
fi

fileid="1tTnOzEoXEZ0W0o44e79uWL0z-uyRmW2l"
filename="data_zipped/vectors.zip"
html=`curl -c ./cookie -s -L "https://drive.google.com/uc?export=download&id=${fileid}"`
curl -Lb ./cookie "https://drive.google.com/uc?export=download&`echo ${html}|grep -Po '(confirm=[a-zA-Z0-9\-_]+)'`&id=${fileid}" -o ${filename}


if [[ -d data/Vectors ]]; then
    rm -rf data/Vectors;
fi

mkdir -p data/Vectors;
unzip data_zipped/vectors.zip -d data/Vectors;
