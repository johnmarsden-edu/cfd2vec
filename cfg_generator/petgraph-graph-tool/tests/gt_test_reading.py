import graph_tool.all as gt

from_gt = gt.load_graph("gt.gt")

from_petgraph = gt.load_graph("petgraph.gt")

assert gt.isomorphism(from_gt, from_petgraph)