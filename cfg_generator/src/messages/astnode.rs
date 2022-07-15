use crate::messages::mixins::{Block, Node, Statement};
use serde::Deserialize;

#[derive(Deserialize)]
pub struct Decision {
    pub true_target: u64,
    pub false_target: u64,
    pub expression: String,
}

#[derive(Deserialize)]
pub enum AstNode {
    FunctionBlock {
        name: Option<String>,
        namespace: Vec<String>,
        parameter: Vec<String>,
        block: Block,
        node: Node,
    },
    LoopBlock {
        initialization: Vec<String>,
        update: Vec<String>,
        decisions: Vec<Decision>,
        first_iteration_decision: String,
        block: Block,
        node: Node,
    },
    TryBlock {
        block: Block,
        catches: Vec<u64>,
        node: Node,
    },
    CatchBlock {
        exception_types: Vec<String>,
        block: Block,
        node: Node,
    },
    ThrowStmt {
        exception: Vec<String>,
        statement: Statement,
    },
    Stmt {
        statement: Statement,
    },
    YieldStmt {
        statement: Statement,
    },
    BreakStmt {
        statement: Statement,
    },
    ContinueStmt {
        statement: Statement,
    },
    ReturnStmt {
        statement: Statement,
    },
}
