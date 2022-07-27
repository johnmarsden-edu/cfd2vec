use petgraph::stable_graph::NodeIndex;

pub(in crate::cfg) struct DecisionTarget<'a> {
    pub(in crate::cfg) true_target: &'a NodeIndex,
    pub(in crate::cfg) false_target: &'a NodeIndex,
}

pub(in crate::cfg) enum BinaryDecision {
    And,
    Or,
}
