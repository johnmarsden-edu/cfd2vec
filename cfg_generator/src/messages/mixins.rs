use serde::Deserialize;

#[derive(Deserialize)]
pub struct Node {
    pub node_type: String,
    pub label: Option<String>,
}

#[derive(Deserialize)]
pub struct Block {
    pub statements: Vec<u64>,
    pub breakable: bool,
    pub continuable: bool,
}

#[derive(Deserialize)]
pub struct Statement {
    pub code: String,
    pub calls: Vec<u64>,
}
