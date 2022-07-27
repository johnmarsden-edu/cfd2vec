use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Node {}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Block {
    pub statements: Vec<u32>,
    pub breakable: bool,
    pub continuable: bool,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Statement {
    pub code: String,
    pub calls: Vec<u32>,
}
