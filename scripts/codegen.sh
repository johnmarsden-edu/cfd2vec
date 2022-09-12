#!/bin/bash

cd schema;

sudo rm -rf codegen;

sudo docker build -t cfg_codegen .;
CID=$(sudo docker create cfg_codegen);
sudo docker cp ${CID}:/app/codegen/ ./;
sudo chown -R $USER codegen/;
sudo docker rm ${CID};

cd ..;

mkdir -p ./cfg_generator/cfg_generator/src/capnp/;
cp -r ./schema/codegen/rust/* ./cfg_generator/cfg_generator/src/capnp/;

mkdir -p ./node_gen_java/app/src/main/java/edu/ncsu/lab/cfg_gen/;
cp -r ./schema/codegen/java/* ./node_gen_java/app/src/main/java/edu/ncsu/lab/cfg_gen/api/;
