from graph_tool.all import *

G = Graph()

word_prop = G.new_vp('string')

G.vp['word'] = word_prop

vertices = G.add_vertex(10)

f_v = next(vertices)

G.vp['word'][f_v] = 'Source'
source_index = int(f_v)

for i, v in enumerate(vertices):
    G.vp['word'][v] = f'word{i}'
    G.add_edge(source_index, int(v), add_missing = True)

print([word_prop[n] for n in G.get_out_neighbors(f_v)])
