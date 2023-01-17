FROM nvcr.io/nvidia/pytorch:22.08-py3

ARG DEBIAN_FRONTEND=noninteractive
ENV TZ=America/New_York

RUN apt-get update
RUN apt-get install -y tzdata
RUN apt-get install -y tree

VOLUME models
VOLUME wandb
VOLUME data

WORKDIR /app

RUN conda update --all
RUN conda config --set channel_priority flexible
RUN conda install -n base conda-forge::mamba

COPY environment.yml /app
RUN mamba env update -n base --file /app/environment.yml
RUN mamba list -n base

RUN mamba install -n base conda-forge::wandb

COPY vocabs/ /app/vocabs
COPY commits_metadata/ /app/commits_metadata
COPY main.py /app
COPY gnn/ /app/gnn

ENTRYPOINT [ "mamba", "run", "-n", "base", "python", "main.py" ]