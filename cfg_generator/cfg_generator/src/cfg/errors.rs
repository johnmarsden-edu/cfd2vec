#[derive(thiserror::Error, Debug)]
pub enum MethodProcessingError {
    #[error("There was an error while reading from the cap'n proto message: {error}")]
    CapnProtoError {
        #[from]
        error: capnp::Error,
    },
    #[error("This functionality is not supported: {0}")]
    NotSupported(String),
    #[error("You provided a starting node that was not a method node")]
    NotAMethod,
    #[error("You provided a function node that was supposed to have a name but did not")]
    TopLevelAnonMethod,
    #[error("You tried to create a do-for loop which has no relation to programming languages")]
    TriedToCreateDoForLoop,
    #[error("You tried to use {0} which is not implemented")]
    NotImplemented(String),
    #[error("There was an error processing the labels: {error}")]
    LabelProcessing {
        #[from]
        error: LabelProcessingError,
    },
    #[error("Cap'n Proto Not in Schema Error")]
    CapnProtoNotInSchema {
        #[from]
        error: capnp::NotInSchema,
    },
}

#[derive(thiserror::Error, Debug)]
pub enum LabelProcessingError {
    #[error("There was an attempt to return to a label")]
    ReturnToLabel,
    #[error("You tried to use {0} which is not implemented")]
    NotImplemented(String),
    #[error("The node had a label but the label was empty")]
    EmptySomeLabel,
}
