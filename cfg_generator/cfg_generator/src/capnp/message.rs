use crate::messages::ast_node::AstNode;
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};
use serde_json::Error;

#[derive(Serialize, Deserialize, JsonSchema)]
#[serde(tag = "type")]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Message {
    pub nodes: Vec<AstNode>,
    pub methods: Vec<u32>,
    pub program_id: String,
}

#[derive(thiserror::Error, Debug)]
pub enum MessageEncodingError {
    #[error("Serde error: {0}")]
    Serde(serde_json::Error),
    #[error(
        "Your data size {0} was too long to be represented in our system which has a max of {1}"
    )]
    Overflow(usize, usize),
}

impl From<serde_json::Error> for MessageEncodingError {
    fn from(e: Error) -> Self {
        MessageEncodingError::Serde(e)
    }
}

impl Message {
    pub fn encode(&self) -> Result<Vec<u8>, MessageEncodingError> {
        let data = serde_json::to_vec(self)?;
        if data.len() > u32::MAX as usize {
            Err(MessageEncodingError::Overflow(
                data.len(),
                u32::MAX as usize,
            ))
        } else {
            let mut result = u32::to_be_bytes(data.len() as u32).to_vec();
            result.extend(data);
            Ok(result)
        }
    }
}
