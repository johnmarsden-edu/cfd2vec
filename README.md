# graph2vec

This tool can be used to turn control flow graphs and/or data flow graphs into embeddings. 

## Setup

1. First, you need to extract the data which you can do by running `extract_data.sh` in the top-level directory of the repo.
2. You can generate the graphs by running ./GraphGenerator/gradlew

## CFG and DFG Generation

We use JavaParser to get the AST for the methods from the java programs, and then turn those into control flow graphs and data flow graphs. 
