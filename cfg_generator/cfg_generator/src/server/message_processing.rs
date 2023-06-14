use crate::capnp::readers::message;
use crate::cfg::{Edge, MethodProcessingError, Node};
use petgraph::stable_graph::StableDiGraph;

#[derive(Debug)]
pub struct ProcessedGraph {
    /// The ID of this particular graph in a group of programs
    pub graph_id: String,
    /// The contents of this particular graph that will be written to a file
    pub graph_contents: Vec<u8>,
}

#[derive(Debug)]
pub struct ProcessedProgram {
    /// The ID of this particular program in this group of programs
    pub program_id: String,
    /// The group that this particular program belongs to
    pub program_group: String,
    /// The graphs contained in this program
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
