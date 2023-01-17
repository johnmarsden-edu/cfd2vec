
from torchtext.vocab import build_vocab_from_iterator
from typing import TextIO
from pickle import dump
# Constants
STRATS = ['NC', 'LC', 'VC', 'FC']


def vocab_words(vocab_file: TextIO):
    yield from ([vocab_word.strip().strip('"')] for vocab_word in vocab_file if vocab_word)



for strat in STRATS:
    unknown = '<unk>'
    anonymous_source = 'anonymous source'
    anonymous_sink = 'anonymous sink'
    anonymous = 'anonymous'
    with open(f'vocab_lists/{strat}_node_vocab_list.txt', 'r') as node_vocab_list, \
         open(f'vocabs/{strat}_node_vocab.pkl', 'wb') as node_vocab_pkl:
        node_vocab = build_vocab_from_iterator(vocab_words(node_vocab_list), specials=[unknown, anonymous_source, anonymous_sink])
        node_vocab.set_default_index(node_vocab[unknown])
        dump(node_vocab, node_vocab_pkl)

    
    with open(f'vocab_lists/{strat}_edge_vocab_list.txt', 'r') as edge_vocab_list, \
         open(f'vocabs/{strat}_edge_vocab.pkl', 'wb') as edge_vocab_pkl:
        edge_vocab = build_vocab_from_iterator(vocab_words(edge_vocab_list), specials=[unknown])
        edge_vocab.set_default_index(edge_vocab[unknown])
        dump(edge_vocab, edge_vocab_pkl)

    with open(f'vocab_lists/{strat}_graph_name_vocab_list.txt', 'r') as graph_name_vocab_list, \
         open(f'vocabs/{strat}_graph_name_vocab.pkl', 'wb') as graph_name_vocab_pkl:
        graph_name_vocab = build_vocab_from_iterator(vocab_words(graph_name_vocab_list), specials=[anonymous])
        dump(graph_name_vocab, graph_name_vocab_pkl)
    