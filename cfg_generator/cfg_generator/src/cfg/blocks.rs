use petgraph::stable_graph::NodeIndex;

#[derive(Clone, Copy)]
pub(in crate::cfg) enum ProcessedBlock {
    Empty,
    Statement(NodeIndex),
    Boundaries {
        start_node: NodeIndex,
        end_node: NodeIndex,
    },
}
