#[derive(thiserror::Error, Debug)]
pub enum MethodProcessingError {
    #[error("This functionality is not supported: {0}")]
    NotSupported(String),
    #[error("You provided a starting node that was not a method node")]
    NotAMethod,
    #[error("You provided a function node that was supposed to have a name but did not")]
    TopLevelAnonMethod,
    #[error("You used a non-decision AST node in a decision tree")]
    UsedNonDecisionNodeInDecision,
    #[error("You tried to create a do-for loop which has no relation to programming languages")]
    TriedToCreateDoForLoop,
    #[error("You tried to use {0} which is not implemented")]
    NotImplemented(String),
    #[error("You passed in an invalid method id {0} which is greater than {1} the length of the list of nodes you provided")]
    InvalidMethodId(u32, u32),
    #[error("You passed in an invalid node id {0} which is greater than {1} the length of the list of nodes you provided")]
    InvalidNodeId(u32, u32),
    #[error("You attempted to process a node twice by passing in an AST with a cycle")]
    ProcessingNodeTwice,
    #[error("You passed in an empty node array which can't be used to generate a graph")]
    EmptyNodeArray,
}
