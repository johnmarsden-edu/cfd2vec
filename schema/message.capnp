@0xdd79c8698f4cded1;
using Java = import "/capnp/java.capnp";
$Java.package("edu.ncsu.lab.cfg_gen.api");
$Java.outerClassname("CfgGenerator");

using Rust = import "/capnp/rust.capnp";
$Rust.parentModule("capnp");

struct Message {
    methods @0 :List(AstNode);
    programId @1 :Text;
    programGroup @4 :Text;
    debug :union {
        some @2 :Text;
        none @3 :Void;
    }
}

struct Block {
    statements @0 :List(AstNode);
}

struct Statement {
    code @0 :Text;
}

struct Condition {
    union {
        and :group {
            left @0 :Condition;
            right @1 :Condition;
        }
        or :group {
            left @2 :Condition;
            right @3 :Condition;
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
    breakable @18 :Bool;
    lambdaFunctions @17 :List(FunctionBlock);
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
        block @16 :Block;
    }
}

struct GotoStatement {
    target @0 :Text;
}

struct ReturnStatement {
    term @2 :Text;
    expression :union{
        some @0 :Text;
        none @1 :Void;
    }
}

struct ContinueStatement {
    term @2 :Text;
    label :union{
        some @0 :Text;
        none @1 :Void;
    }
}

struct BreakStatement {
    term @2 :Text;
    label :union{
        some @0 :Text;
        none @1 :Void;
    }
}

struct YieldStatement {
    term @1 :Text;
    statement @0 :Statement;
}

struct ThrowStatement {
    term @2 :Text;
    exception @0 :List(Text);
    statement @1 :Text;
}

struct CatchBlock {
    exceptionTypes @0 :List(Text);
    block @1 :Block;
}

struct TryBlock {
    block @0 :Block;
    catches @1 :List(CatchBlock);
    finally :union {
        some @2 :Block;
        none @3 :Void;
    }
}

struct DecisionBlock {
    condition @0 :Condition;
    block @1 :Block;
    else :union {
        some @2 :AstNode;
        none @3 :Void;
    }
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
