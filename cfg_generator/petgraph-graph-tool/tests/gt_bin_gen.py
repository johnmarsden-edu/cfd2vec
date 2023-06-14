import graph_tool.all as gt

# gt.load_graph("test.gt")

g = gt.Graph()

g.save("empty.gt")

pg = g.add_vertex()
fb = g.add_vertex()
qc = g.add_vertex()
rand = g.add_vertex()
libc = g.add_vertex()

edge_number_prop = g.new_edge_property("int")

g.add_edge_list([
    (pg, fb, 0),
    (pg, qc, 1),
    (qc, rand, 2),
    (rand, libc, 3),
    (qc, libc, 4),
], eprops=[edge_number_prop])

node_name_prop = g.new_vertex_property("string")

node_name_prop[pg] = "petgraph"
node_name_prop[fb] = "fixedbitset"
node_name_prop[qc] = "quickcheck"
node_name_prop[rand] = "rand"
node_name_prop[libc] = "libc"


g.vp["name"] = node_name_prop
g.ep["number"] = edge_number_prop

g.save("gt.gt")
