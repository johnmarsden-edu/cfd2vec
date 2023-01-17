from pathlib import Path
import torch.nn.functional as F
import torch
from pickle import load
from torch_geometric.data import Data
from torch_geometric.loader import DataLoader
import wandb
from gnn.graph_reader import GraphMLDataset
from gnn.dgcn import LearnableEmbeddings

from typing import TYPE_CHECKING

if TYPE_CHECKING:
    from torchtext.vocab import Vocab

wandb.init(project='gnn-ml-w-graphs-project')
wandb.config = {
    'learning_rate': 0.01,
    'epochs': 10,
    'batch_size': 32
}

PROCESSED_DATASETS_DIR = Path.cwd() / 'processed_datasets'
GRAPH_DIR = Path.cwd() / '..' / 'data' / 'generated_graphs'
STRATS = ['NC', 'LC', 'VC', 'FC']

for strat in STRATS:
    print(f'Creating the {strat} model now')
    # unpickle the vocab
    with open(f'vocabs/{strat}_node_vocab.pkl', 'rb') as node_vocab_pickle, \
         open(f'vocabs/{strat}_edge_vocab.pkl', 'rb') as edge_vocab_pickle, \
         open(f'vocabs/{strat}_graph_name_vocab.pkl', 'rb') as graph_name_vocab_pickle, \
         open(f'commits_metadata/{strat}_commits_files_list.pkl', 'rb') as commits_files_list_pickle:
        
        node_vocab: Vocab = load(node_vocab_pickle)
        edge_vocab: Vocab = load(edge_vocab_pickle)
        graph_name_vocab: Vocab = load(graph_name_vocab_pickle)
        commits_files_list: list[str] = load(commits_files_list_pickle)


    # create graph ml dataset
    dataset = GraphMLDataset(PROCESSED_DATASETS_DIR, node_vocab, edge_vocab, graph_name_vocab, commits_files_list, GRAPH_DIR)
    print(f'There are {len(node_vocab)} nodes in our node vocab')
    print(f'There are {len(edge_vocab)} edges in our edge vocab')
    print(f'There are {len(graph_name_vocab)} graph names in our graph name vocab')


    torch.manual_seed(19482383)
    dataset = dataset.shuffle()

    if isinstance(dataset, tuple):
        dataset = dataset[0]

    train_ds_len = int(len(dataset) * .7)
    val_ds_len = int(len(dataset) * .1)
    test_ds_len = len(dataset) - train_ds_len - val_ds_len

    print(f'Length of dataset before splitting: {len(dataset)}')

    print(f'Length of training dataset: {train_ds_len}')
    train_dataset = dataset[:train_ds_len]

    if isinstance(train_dataset, Data):
        continue

    print(f'Length of validation dataset: {val_ds_len}')
    validation_dataset = dataset[train_ds_len:train_ds_len + val_ds_len]

    if isinstance(validation_dataset, Data):
        continue

    print(f'Length of test dataset: {test_ds_len}')
    test_dataset = dataset[train_ds_len + val_ds_len:]

    if isinstance(test_dataset, Data):
        continue

    print(f'Length of all 3 datasets: {train_ds_len} + {val_ds_len} + {test_ds_len} = {train_ds_len + val_ds_len + test_ds_len}')

    BATCH_SIZE = wandb.config['batch_size']

    # feed the dataset into the pyg dataloader
    train_dataloader = DataLoader(train_dataset, batch_size=BATCH_SIZE, shuffle=True)
    validation_dataloader = DataLoader(validation_dataset, batch_size=BATCH_SIZE, shuffle=False)
    test_dataloader = DataLoader(test_dataset, batch_size=BATCH_SIZE, shuffle=False)

    # TODO: Consider adding two hop edges to nodes

    # create the model
    device = torch.device('cuda') if torch.cuda.is_available() else torch.device('cpu')
    print(f'Device: {device}')
    model = LearnableEmbeddings(len(node_vocab), 256, len(edge_vocab), 4, edge_vocab[''], 512, len(graph_name_vocab)).to(device)
    optimizer = torch.optim.Adam(model.parameters(), lr=wandb.config['learning_rate'], weight_decay=5e-4)

    # train the model
    EPOCHS = wandb.config['epochs']
    criterion = torch.nn.NLLLoss()
    wandb.watch(model, criterion, 'all', 1)
    for epoch in range(EPOCHS):
        model.train()
        for batch_num, data in enumerate(train_dataloader):
            if batch_num % 10000 == 0:
                print(f"Finished batch {batch_num} in epoch {epoch} for strategy {strat} model")
            optimizer.zero_grad()
            out = model(data)
            expected = data.graph_name_index - data._store._inc_dict['graph_name_index']
            loss = criterion(out, expected)
            loss.backward()
            wandb.log({'training_loss': loss})
            optimizer.step()

        model.eval()
        correct = 0
        total = 0
        for data in validation_dataloader:
            out = model(data).argmax(1)
            expected = data.graph_name_index - data._store._inc_dict['graph_name_index']
            correct += int((out == expected).sum())
            total += len(expected)
        
        acc = correct / total
        print(f'Accuracy in epoch {epoch} on validation set: {acc:.4f}')

    # test the model
    model.eval()
    correct = 0
    total = 0
    for data in test_dataloader:
        out = model(data).argmax(1)
        expected = data.graph_name_index - data._store._inc_dict['graph_name_index']
        correct += int((out == expected).sum())
        total += len(expected)

    acc = correct / total
    print(f'Accuracy on test set: {acc:.4f}')

    # output the results
    torch.save(model.state_dict(), f'models/{strat}_model.pt')