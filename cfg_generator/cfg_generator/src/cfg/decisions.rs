use petgraph::stable_graph::NodeIndex;

pub(in crate::cfg) struct DecisionTarget {
    pub(in crate::cfg) true_target: NodeIndex,
    pub(in crate::cfg) false_target: NodeIndex,
}

pub(in crate::cfg) enum BinaryDecision {
    And,
    Or,
}
