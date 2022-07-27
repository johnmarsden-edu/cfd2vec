mod ast_processor;
mod blocks;
pub mod cfg_node;
mod decisions;
mod errors;

pub use cfg_node::{CfgEdge, CfgNode};
pub use errors::MethodProcessingError;

use crate::cfg::ast_processor::AstProcessor;
use crate::messages::ast_node::*;

use petgraph::stable_graph::StableDiGraph;
use std::borrow::Borrow;

pub fn process_method(
    nodes: &[AstNode],
    method_id: usize,
) -> Result<StableDiGraph<CfgNode, CfgEdge>, MethodProcessingError> {
    if nodes.is_empty() {
        Err(MethodProcessingError::EmptyNodeArray)
    } else if method_id >= nodes.len() {
        Err(MethodProcessingError::InvalidMethodId(
            method_id as u32,
            nodes.len() as u32,
        ))
    } else {
        AstProcessor::process_method(nodes, method_id, nodes[method_id].borrow())
    }
}
