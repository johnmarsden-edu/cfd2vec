use crate::capnp::readers::message;
use crate::cfg::{Edge, MethodProcessingError, Node};
use crate::db::cornucopia;
use petgraph::stable_graph::{NodeIndex, StableDiGraph};

#[derive(Debug)]
pub struct ProcessedNode {
    pub node_index: NodeIndex,
    pub label: Option<String>,
    pub node_type: cornucopia::types::public::NodeType,
    pub contents: Option<String>,
}

#[derive(Debug)]
pub struct ProcessedEdge {
    pub source: NodeIndex,
    pub target: NodeIndex,
    pub edge_type: cornucopia::types::public::EdgeType,
    pub direction: Option<bool>,
    pub exception: Option<String>,
}

#[derive(Debug)]
pub struct ProcessedGraph {
    pub graph_id: String,
    pub nodes: Vec<ProcessedNode>,
    pub edges: Vec<ProcessedEdge>,
}

#[derive(Debug)]
pub struct ProcessedProgram {
    pub program_id: String,
    pub graphs: Vec<ProcessedGraph>,
}

type MessageProcessingResult<'a> =
    Result<Vec<(Option<String>, StableDiGraph<Node<'a>, Edge<'a>>)>, MethodProcessingError>;

pub(crate) fn process_message<'a>(message: &message::Reader<'a>) -> MessageProcessingResult<'a> {
    let mut graphs = Vec::new();
    for node in message.get_methods()? {
        match crate::cfg::process_method(node) {
            Ok(processed) => graphs.push(processed),
            Err(e) => Err(e)?,
        }
    }
    Ok(graphs)
}
