#[derive(Debug)]
pub struct CfgNode<'a> {
    pub label: Option<&'a String>,
    pub node_type: CfgNodeType<'a>,
}

#[derive(Debug)]
pub enum ControlNodeType {
    Break,
    _Yield,
    Return,
    Continue,
}
#[derive(Debug)]
pub enum CfgNodeType<'a> {
    Source { name: Option<&'a String> },
    Sink { name: Option<&'a String> },
    Statement { statement: &'a String },
    ControlNode(ControlNodeType),
    Decision { decision: &'a String },
    Exception { statement: &'a String },
    Label,
}

#[derive(Copy, Clone, Debug)]
pub enum CfgEdge<'a> {
    Statement,
    Decision { direction: bool },
    Exception { exception: &'a str },
    ContinueLabel,
    BreakLabel,
}
