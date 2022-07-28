#[derive(Debug)]
pub struct CfgNode<'a> {
    pub label: Option<&'a str>,
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
    Source { name: Option<&'a str> },
    Sink { name: Option<&'a str> },
    Statement { statement: &'a str },
    ControlNode(ControlNodeType),
    Decision { decision: &'a str },
    Exception { statement: &'a str },
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
