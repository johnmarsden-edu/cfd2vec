use crate::messages::mixins::{Block, Node, Statement};
use schemars::JsonSchema;
use serde::{Deserialize, Serialize};

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub enum Decision {
    And(u32, u32),
    Or(u32, u32),
    Unit(String),
    Empty,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct FunctionBlock {
    pub name: Option<String>,
    pub namespace: Vec<String>,
    pub parameter: Vec<String>,
    #[serde(flatten)]
    pub block: Block,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct LoopBlock {
    pub initialization: Vec<String>,
    pub update: Vec<String>,
    pub decision: Decision,
    pub first_iteration_decision: bool,
    #[serde(flatten)]
    pub block: Block,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct DecisionBlock {
    pub decision: Decision,
    #[serde(flatten)]
    pub block: Block,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct TryBlock {
    #[serde(flatten)]
    pub block: Block,
    pub catches: Vec<u32>,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct CatchBlock {
    pub exception_types: Vec<String>,
    #[serde(flatten)]
    pub block: Block,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct ThrowStatement {
    pub exception: Vec<String>,
    #[serde(flatten)]
    pub statement: Statement,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Stmt {
    #[serde(flatten)]
    pub statement: Statement,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct YieldStatement {
    #[serde(flatten)]
    pub statement: Statement,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Break {
    pub label: Option<String>,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Continue {
    pub label: Option<String>,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Return {
    pub expression: Option<String>,
    #[serde(flatten)]
    pub node: Node,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct Goto {
    pub label: String,
    #[serde(flatten)]
    pub node: Node,
}

#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub enum Exits {
    Yes,
    No,
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub enum AstNodeContents {
    FunctionBlock(FunctionBlock),
    LoopBlock(LoopBlock),
    DecisionBlock(DecisionBlock),
    TryBlock(TryBlock),
    CatchBlock(CatchBlock),
    ThrowStatement(ThrowStatement),
    Statement(Stmt),
    YieldStatement(YieldStatement),
    Break(Break),
    Continue(Continue),
    Return(Return),
    Decision(Decision),
    Goto(Goto),
}

#[derive(Serialize, Deserialize, JsonSchema)]
#[cfg_attr(
    feature = "arbitrary",
    derive(arbitrary::Arbitrary, Debug, Eq, PartialEq)
)]
pub struct AstNode {
    pub node_type: String,
    pub label: Option<String>,
    pub contents: AstNodeContents,
}

impl AstNode {
    pub fn exits(&self) -> Exits {
        match self.contents {
            AstNodeContents::ThrowStatement(_)
            | AstNodeContents::YieldStatement(_)
            | AstNodeContents::Break(_)
            | AstNodeContents::Continue(_)
            | AstNodeContents::Return(_) => Exits::Yes,
            _ => Exits::No,
        }
    }
}
