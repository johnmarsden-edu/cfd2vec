mod ast_processor;
mod blocks;
pub mod cfg_node;
mod decisions;
mod errors;

pub use cfg_node::{CfgEdge, CfgNode};
use error_chain::bail;
pub use errors::MethodProcessingError;

use crate::cfg::ast_processor::AstProcessor;
pub use crate::cfg::ast_processor::Error;
pub use crate::cfg::ast_processor::Result;

use petgraph::stable_graph::StableDiGraph;

pub fn process_method(
    nodes: capnp::struct_list::Reader<crate::capnp::message_capnp::ast_node::Owned>,
    method_id: u32,
) -> Result<StableDiGraph<CfgNode, CfgEdge>> {
    if nodes.len() == 0 {
        bail!(MethodProcessingError::EmptyNodeArray)
    } else if method_id >= nodes.len() {
        bail!(MethodProcessingError::InvalidMethodId(
            method_id as u32,
            nodes.len() as u32,
        ))
    } else {
        AstProcessor::process_method(nodes, method_id, nodes.get(method_id))
    }
}
