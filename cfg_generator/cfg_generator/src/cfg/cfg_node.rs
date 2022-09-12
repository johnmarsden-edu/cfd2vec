use std::fmt::{Display, Formatter};

#[derive(Debug)]
pub struct Node<'a> {
    pub label: Option<&'a str>,
    pub node_type: NodeType<'a>,
}

impl<'a> Display for Node<'a> {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self.label {
            Some(label) => write!(f, "{}: {}", label, self.node_type),
            None => write!(f, "{}", self.node_type),
        }
    }
}

#[derive(Debug)]
pub enum ControlType<'a> {
    Break(&'a str),
    _Yield(&'a str),
    Return(&'a str),
    Continue(&'a str),
}

#[derive(Debug)]
pub enum NodeType<'a> {
    Source {
        name: Option<&'a str>,
    },
    Sink {
        name: Option<&'a str>,
    },
    Statement {
        statement: &'a str,
    },
    ControlNode {
        control_type: ControlType<'a>,
        contents: Option<&'a str>,
    },
    Decision {
        decision: &'a str,
    },
    Exception {
        term: &'a str,
        statement: &'a str,
    },
    Label,
}

impl<'a> Display for NodeType<'a> {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            NodeType::Source { name } => match name {
                Some(name) => write!(f, "Source {}", name),
                None => write!(f, "Anonymous Source"),
            },
            NodeType::Sink { name } => match name {
                Some(name) => write!(f, "Sink {}", name),
                None => write!(f, "Anonymous Sink"),
            },
            NodeType::Statement { statement } => write!(f, "statement: {}", statement),
            NodeType::ControlNode {
                control_type,
                contents,
            } => {
                write!(f, "control node: {:?} {:?}", control_type, contents)
            }
            NodeType::Decision { decision } => write!(f, "decision: {}", decision),
            NodeType::Exception { term, statement } => {
                write!(f, "throws using {}: {}", term, statement)
            }
            NodeType::Label => write!(f, "Dummy Label Node"),
        }
    }
}

#[derive(Copy, Clone, Debug)]
pub enum Edge<'a> {
    Statement,
    Decision(Direction),
    Exception { exception: &'a str },
    Label(LabelEdge),
}

impl<'a> Edge<'a> {
    pub fn is_break(&self) -> bool {
        matches!(self, Edge::Label(LabelEdge::Break))
    }

    pub fn is_continue(&self) -> bool {
        matches!(self, Edge::Label(LabelEdge::Continue))
    }

    pub fn is_label_next(&self) -> bool {
        matches!(self, Edge::Label(LabelEdge::Next))
    }
}

#[derive(Copy, Clone, Debug)]
pub enum Direction {
    True,
    False,
}

#[derive(Copy, Clone, Debug)]
pub enum LabelEdge {
    Continue,
    Break,
    Next,
}

impl<'a> Display for Edge<'a> {
    fn fmt(&self, f: &mut Formatter<'_>) -> std::fmt::Result {
        match self {
            Edge::Statement => write!(f, ""),
            Edge::Decision(direction) => {
                write!(f, "{:#?}", direction)
            }
            Edge::Exception { exception } => write!(f, "{}", exception),
            Edge::Label(edge_type) => {
                write!(f, "{:#?}", edge_type)
            }
        }
    }
}
