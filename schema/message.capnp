@0xdd79c8698f4cded1;

struct Message {
    nodes @0 :List(AstNode);
    methods @1 :List(UInt32);
    programId @2 :Text;
}

struct Block {
    statements @0 :List(UInt32);
}

struct Statement {
    code @0 :Text;
}

struct Condition {
    union {
        and :group {
            left @0 :UInt32;
            right @1 :UInt32;
        }
        or :group {
            left @2 :UInt32;
            right @3 :UInt32;
        }
        unit @4 :Text;
        empty @5 :Void;
    }
}

struct AstNode {
    nodeType @0 :Text;
    label :union {
        some @1 :Text;
        none @2 :Void;
    }
    contents :union {
        functionBlock @3 :FunctionBlock;
        loopBlock @4 :LoopBlock;
        decisionBlock @5 :DecisionBlock;
        tryBlock @6 :TryBlock;
        catchBlock @7 :CatchBlock;
        throwStatement @8 :ThrowStatement;
        statement @9 :Statement;
        yieldStatement @10 :YieldStatement;
        breakStatement @11 :BreakStatement;
        continueStatement @12 :ContinueStatement;
        returnStatement @13 :ReturnStatement;
        condition @14 :Condition;
        gotoStatement @15 :GotoStatement;
    }
}

struct GotoStatement {
    target @0 :Text;
}

struct ReturnStatement {
    expression :union{
        some @0 :Text;
        none @1 :Void;
    }
}

struct ContinueStatement {
    label :union{
        some @0 :Text;
        none @1 :Void;
    }
}

struct BreakStatement {
    label :union{
        some @0 :Text;
        none @1 :Void;
    }
}

struct YieldStatement {
    statement @0 :Statement;
}

struct ThrowStatement {
    exception @0 :List(Text);
    statement @1 :Text;
}

struct CatchBlock {
    exceptionTypes @0 :List(Text);
    block @1 :Block;
}

struct TryBlock {
    block @0 :Block;
    catches @1 :List(UInt32);
}

struct DecisionBlock {
    condition @0 :Condition;
    block @1 :Block;
}

struct LoopBlock {
    initialization @0 :List(Text);
    update @1 :List(Text);
    condition @2 :Condition;
    firstIterationConditionCheck @3 :Bool;
    block @4 :Block;
}

struct FunctionBlock {
    name :union{
        some @0 :Text;
        none @1 :Void;
    }
    namespace @2 :List(Text);
    parameters @3 :List(Text);
    block @4 :Block;
}