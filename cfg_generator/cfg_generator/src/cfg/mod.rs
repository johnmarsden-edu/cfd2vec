mod ast_processor;
mod blocks;
pub mod cfg_node;
mod decisions;
mod errors;

use crate::capnp::readers::{ast_node, function_block};
use crate::cfg::ast_processor::AstProcessor;
pub use cfg_node::{Edge, Node};
pub use errors::MethodProcessingError;
use petgraph::stable_graph::StableDiGraph;

pub fn process_method(
    method: ast_node::Reader,
) -> Result<(Option<String>, StableDiGraph<Node, Edge>), MethodProcessingError> {
    Ok((
        if let ast_node::contents::FunctionBlock(fb) = method.get_contents().which()? {
            match fb?.get_name().which()? {
                function_block::name::Some(name) => Some(name?.to_string()),
                _ => None,
            }
        } else {
            Err(MethodProcessingError::NotAMethod)?
        },
        AstProcessor::process_method(method)?,
    ))
}
