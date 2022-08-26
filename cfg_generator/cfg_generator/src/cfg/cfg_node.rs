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
pub enum ControlType {
    Break,
    _Yield,
    Return,
    Continue,
}

#[derive(Debug)]
pub enum NodeType<'a> {
    Source { name: Option<&'a str> },
    Sink { name: Option<&'a str> },
    Statement { statement: &'a str },
    ControlNode(ControlType),
    Decision { decision: &'a str },
    Exception { statement: &'a str },
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
            NodeType::ControlNode(control_node_type) => {
                write!(f, "control node: {:?}", control_node_type)
            }
            NodeType::Decision { decision } => write!(f, "decision: {}", decision),
            NodeType::Exception { statement } => write!(f, "throws: {}", statement),
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
