FROM nvcr.io/nvidia/pytorch:22.12-py3

ARG DEBIAN_FRONTEND=noninteractive
ENV TZ=America/New_York

RUN apt-get update
RUN apt-get install -y tzdata
RUN apt-get install -y tree

VOLUME models
VOLUME wandb
VOLUME data

WORKDIR /app


# Add graph-tool to system
RUN echo "deb [ arch=amd64 ] https://downloads.skewed.de/apt focal main" >> /etc/apt/sources.list
RUN apt-key adv --keyserver keyserver.ubuntu.com --recv-key 612DEFB798507F25
RUN apt-get update
RUN apt-get install -y python3-graph-tool

# Add pyg to the system

RUN pip install git+https://github.com/pyg-team/pyg-lib.git
RUN pip install torch-scatter
RUN pip install torch-sparse
RUN pip install torch-geometric
RUN pip install torch-cluster
RUN pip install torch-spline-conv
RUN pip install wandb
RUN pip install pandas
RUN pip install torchvision
RUN pip install torchtext
RUN pip install lxml

COPY vocabs/ /app/vocabs
COPY graphs_metadata/ /app/graphs_metadata
COPY main.py /app
COPY gnn/ /app/gnn

ENTRYPOINT [ "python3", "main.py" ]