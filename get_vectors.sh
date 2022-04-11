#!/bin/bash



if [[ -f data_zipped/vectors.zip ]]; then
    rm data_zipped/vectors.zip;
fi

wget --load-cookies /tmp/cookies.txt "https://docs.google.com/uc?export=download&confirm=$(wget --quiet --save-cookies /tmp/cookies.txt --keep-session-cookies --no-check-certificate 'https://docs.google.com/uc?export=download&id=1tTnOzEoXEZ0W0o44e79uWL0z-uyRmW2l' -O- | sed -rn 's/.*confirm=([0-9A-Za-z_]+).*/\1\n/p')&id=1tTnOzEoXEZ0W0o44e79uWL0z-uyRmW2l" -O data_zipped/vectors.zip && rm -rf /tmp/cookies.txt;

if [[ -d data/Vectors ]]; then
    rm -rf data/Vectors;
fi

mkdir -p data/Vectors;
unzip data_zipped/vectors.zip -d data/Vectors;
