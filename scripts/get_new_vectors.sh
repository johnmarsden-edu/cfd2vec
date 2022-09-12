#!/bin/bash



if [[ -f data_zipped/vectors.zip ]]; then
    rm data_zipped/vectors.zip;
fi

fileid="1eq_6RHO_eL9RlO_ALZi1exrszhPEArmy"
filename="data_zipped/vectors.zip"
html=`curl -c ./cookie -s -L "https://drive.google.com/uc?export=download&id=${fileid}"`
curl -Lb ./cookie "https://drive.google.com/uc?export=download&`echo ${html}|grep -Po '(confirm=[a-zA-Z0-9\-_]+)'`&id=${fileid}" -o ${filename}


mkdir -p data/Vectors;
unzip data_zipped/vectors.zip -d data/Vectors;
