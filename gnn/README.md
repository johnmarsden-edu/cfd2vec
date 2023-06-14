To build the docker image as fast as possible, while in the the directory this readme if you are on a linux system run 

```
DOCKER_BUILDKIT=1 docker build .
```

otherwise just run

```
docker build .
```

Then, to run this, you use the following command:

```
docker run -e WANDB_API_KEY="393db7cc5523ac7edefe84745bb7bc4f622c569b" \
    --mount type=bind,source="$(pwd)"/models,target=/models \
    --mount type=bind,source="$(pwd)"/../data,target=/data,readonly \
    --mount type=bind,source="$(pwd)"/wandb,target=/wandb \
    gnn
```