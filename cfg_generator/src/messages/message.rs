use crate::messages::astnode::AstNode;
use serde::{Deserialize, Deserializer};

#[derive(Deserialize)]
pub struct Message {
    pub nodes: Vec<AstNode>,
    pub methods: Vec<u64>,
    pub entrypoint: u64,
}
