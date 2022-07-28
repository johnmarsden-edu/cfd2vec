use std::borrow::Borrow;
use std::collections::{HashMap, HashSet};

use capnp::struct_list;
use error_chain::{bail, error_chain};
use itertools::Itertools;
use petgraph::prelude::EdgeRef;
use petgraph::stable_graph::{NodeIndex, StableDiGraph};
use petgraph::visit::Bfs;
use petgraph::Direction;

use crate::capnp::message_capnp::ast_node;
use crate::capnp::readers::{
    block, break_statement, catch_block, condition, continue_statement, decision_block,
    function_block, loop_block, return_statement, statement, throw_statement, try_block,
    yield_statement,
};
use crate::cfg::blocks::ProcessedBlock;
use crate::cfg::cfg_node::{CfgNode, CfgNodeType, ControlNodeType};
use crate::cfg::decisions::{BinaryDecision, DecisionTarget};
use crate::cfg::{CfgEdge, MethodProcessingError};

error_chain!(
    foreign_links {
        Io(std::io::Error);
        CapnP(capnp::Error);
        NotInSchema(capnp::NotInSchema);
        MethodProcessingError(MethodProcessingError);
    }
);

#[derive(Copy, Clone)]
struct ProcessorContext<'a, 'b> {
    label: Option<&'a str>,
    most_recent_node: Option<NodeIndex>,
    nearest_breakable_node: Option<&'b NodeIndex>,
    nearest_continueable_node: Option<&'b NodeIndex>,
    sink_node: Option<&'b NodeIndex>,
    exception_nodes: &'b HashMap<&'b str, NodeIndex>,
}

enum NodeType {
    Child(NodeIndex),
    Root,
}

pub enum Exits {
    Yes,
    No,
}

pub struct AstProcessor<'a> {
    nodes: struct_list::Reader<'a, ast_node::Owned>,
    pub method_graph: StableDiGraph<CfgNode<'a>, CfgEdge<'a>>,
    already_visited: HashSet<u32>,
}
impl<'a> AstProcessor<'a> {
    // Public Methods

    /// A method for them to interact with the API
    pub fn process_method(
        nodes: struct_list::Reader<'a, ast_node::Owned>,
        method_id: u32,
        start_node: ast_node::Reader<'a>,
    ) -> Result<StableDiGraph<CfgNode<'a>, CfgEdge<'a>>> {
        if let ast_node::contents::FunctionBlock(Ok(f)) = start_node.get_contents().which()? {
            if f.get_name().has_some() {
                let mut ast_processor = AstProcessor {
                    nodes,
                    method_graph: StableDiGraph::new(),
                    already_visited: HashSet::from([method_id; 1]),
                };

                let base_context = ProcessorContext {
                    label: None,
                    most_recent_node: None,
                    nearest_breakable_node: None,
                    nearest_continueable_node: None,
                    sink_node: None,
                    exception_nodes: &HashMap::new(),
                };

                ast_processor.process_function(base_context, f)?;

                Ok(ast_processor.method_graph)
            } else {
                bail!(MethodProcessingError::TopLevelAnonMethod)
            }
        } else {
            bail!(MethodProcessingError::NotAMethod)
        }
    }

    fn process_labels(&mut self, start_node: NodeIndex) -> Result<()> {
        let mut traversal = Bfs::new(&self.method_graph, start_node);
        let mut labels: HashMap<&str, NodeIndex> = HashMap::new();
        while let Some(current) = traversal.next(&self.method_graph) {
            match &self.method_graph[current].node_type {
                CfgNodeType::ControlNode(type_) => {
                    if let Some(label) = self.method_graph[current].label {
                        if let Some(label_node) = labels.get(label) {
                            match type_ {
                                ControlNodeType::Break => {
                                    let break_edge = self
                                        .method_graph
                                        .edges(*label_node)
                                        .find(|e| matches!(e.weight(), CfgEdge::BreakLabel));
                                    if let Some(target) = break_edge.map(|e| e.target()) {
                                        self.add_statement_edge(&current, &target);
                                    }
                                    Ok(())
                                }
                                ControlNodeType::_Yield => {
                                    Err(MethodProcessingError::NotImplemented(
                                        "Yield statement is not yet implemented".to_string(),
                                    ))
                                }
                                ControlNodeType::Return => {
                                    Err(MethodProcessingError::NotSupported(
                                        "You attempted to return to a label".to_string(),
                                    ))
                                }
                                ControlNodeType::Continue => {
                                    let continue_edge = self
                                        .method_graph
                                        .edges(*label_node)
                                        .find(|e| matches!(e.weight(), CfgEdge::ContinueLabel));
                                    if let Some(target) = continue_edge.map(|e| e.target()) {
                                        self.add_statement_edge(&current, &target);
                                    }
                                    Ok(())
                                }
                            }?;
                        }
                    }
                }
                CfgNodeType::Label => {
                    if let Some(label) = self.method_graph[current].label {
                        labels.insert(label, current);
                    }
                }
                _ => {}
            }
        }

        Ok(())
    }

    // Helper functions

    /// Get the AST Node from the internal state
    fn get_node(&self, node_id: u32) -> Result<ast_node::Reader<'a>> {
        if node_id >= self.nodes.len() {
            bail!(MethodProcessingError::InvalidNodeId(
                node_id,
                self.nodes.len() as u32,
            ))
        } else {
            Ok(self.nodes.get(node_id))
        }
    }

    /// A function to split nodes to their respective handlers
    ///
    /// The following handle other types of nodes as well
    /// LoopBlock - Decision nodes
    /// DecisionBlock - Decision nodes
    /// TryBlock - CatchBlock nodes
    fn process_node(
        &mut self,
        ctx: ProcessorContext,
        node_id: u32,
        node: ast_node::Reader<'a>,
    ) -> Result<NodeType> {
        if self.already_visited.contains(&node_id) {
            bail!(MethodProcessingError::ProcessingNodeTwice)
        } else {
            self.already_visited.insert(node_id);
            let ctx = ProcessorContext {
                label: match node.get_label().which()? {
                    ast_node::label::Some(label) => Some(label?),
                    ast_node::label::None(()) => None,
                },
                ..ctx
            };
            match node.get_contents().which()? {
                ast_node::contents::FunctionBlock(fb) => self.process_function(ctx, fb?),
                ast_node::contents::LoopBlock(lb) => self.process_loop(ctx, lb?),
                ast_node::contents::DecisionBlock(db) => self.process_decision_block(ctx, db?),
                ast_node::contents::TryBlock(tb) => self.process_try(ctx, tb?),
                ast_node::contents::ThrowStatement(ts) => self.process_throw(ctx, ts?),
                ast_node::contents::Statement(s) => self.process_statement(ctx,s?),
                ast_node::contents::YieldStatement(ys) => self.process_yield(ctx, ys?),
                ast_node::contents::BreakStatement(bs) => self.process_break(ctx, bs?),
                ast_node::contents::ContinueStatement(cs) => self.process_continue(ctx, cs?),
                ast_node::contents::ReturnStatement(rs) => self.process_return(ctx, rs?),
                _ => bail!(MethodProcessingError::NotSupported("You have a node in your blocks that can only be unpacked in a specific type of node.".to_string()))
            }
        }
    }

    // Add node helper methods

    /// Add a decision node with the given decision expression to the graph
    fn add_decision_node(&mut self, decision: &'a str) -> NodeIndex {
        self.method_graph.add_node(CfgNode {
            label: None,
            node_type: CfgNodeType::Decision { decision },
        })
    }

    /// Add a statement node with the given statement to the graph
    fn add_statement_node(&mut self, label: Option<&'a str>, stmt: &'a str) -> NodeIndex {
        self.method_graph.add_node(CfgNode {
            label,
            node_type: CfgNodeType::Statement { statement: stmt },
        })
    }

    /// Add a list of statement nodes with the given statement values to the graph linking them
    /// one to the next in order
    fn add_and_link_statement_nodes<'b>(
        &mut self,
        statements: &'b [(Option<&'a str>, &'a str)],
    ) -> Vec<NodeIndex> {
        let nodes: Vec<NodeIndex> = statements
            .iter()
            .map(|(label, s)| self.add_statement_node(*label, s))
            .collect();

        for (start, end) in nodes.iter().tuple_windows() {
            self.add_statement_edge(start, end);
        }

        nodes
    }

    fn add_exception_node(&mut self, stmt: &'a str) -> NodeIndex {
        self.method_graph.add_node(CfgNode {
            label: None,
            node_type: CfgNodeType::Exception { statement: stmt },
        })
    }

    /// Add a statement node with the given statement to the graph
    fn add_control_node(
        &mut self,
        label: Option<&'a str>,
        node_type: ControlNodeType,
    ) -> NodeIndex {
        self.method_graph.add_node(CfgNode {
            label,
            node_type: CfgNodeType::ControlNode(node_type),
        })
    }

    // Add edge helper methods

    /// Add a chosen `direction` decision edge to the graph connecting the `start` node
    /// and `end` node
    fn add_decision_edge(
        &mut self,
        start: &NodeIndex,
        end: &NodeIndex,
        direction: bool,
    ) -> petgraph::stable_graph::EdgeIndex {
        self.method_graph
            .add_edge(*start, *end, CfgEdge::Decision { direction })
    }

    /// Add a statement edge to the graph connecting the `start` node and `end` node
    fn add_statement_edge(
        &mut self,
        start: &NodeIndex,
        end: &NodeIndex,
    ) -> petgraph::stable_graph::EdgeIndex {
        self.method_graph.add_edge(*start, *end, CfgEdge::Statement)
    }

    fn add_exception_edge(
        &mut self,
        start: &NodeIndex,
        end: &NodeIndex,
        exception_type: &'a str,
    ) -> petgraph::stable_graph::EdgeIndex {
        self.method_graph.add_edge(
            *start,
            *end,
            CfgEdge::Exception {
                exception: exception_type.borrow(),
            },
        )
    }

    fn exits(node: ast_node::Reader) -> Result<Exits> {
        match node.get_contents().which()? {
            ast_node::contents::ThrowStatement(_)
            | ast_node::contents::YieldStatement(_)
            | ast_node::contents::BreakStatement(_)
            | ast_node::contents::ContinueStatement(_)
            | ast_node::contents::ReturnStatement(_) => Ok(Exits::Yes),
            _ => Ok(Exits::No),
        }
    }

    fn process_block(
        &mut self,
        ctx: ProcessorContext,
        block: block::Reader<'a>,
    ) -> Result<ProcessedBlock> {
        let mut block_context = ctx;
        let nodes: Vec<(NodeIndex, Exits)> = block
            .get_statements()?
            .iter()
            .rev()
            .map(|s| {
                let ast_node = self.get_node(s)?;
                let result = self.process_node(block_context, s, ast_node)?;
                match result {
                    NodeType::Child(node) => {
                        block_context = ProcessorContext {
                            most_recent_node: Some(node),
                            ..block_context
                        };
                        Ok(Some((node, AstProcessor::exits(ast_node)?)))
                    }
                    NodeType::Root => Ok(None),
                }
            })
            .filter_map_ok(|opt| opt)
            .collect::<Result<Vec<(NodeIndex, Exits)>>>()?;

        for (before, after) in nodes
            .iter()
            .tuple_windows::<(&(NodeIndex, Exits), &(NodeIndex, Exits))>()
        {
            if let Exits::No = before.1 {
                self.add_statement_edge(&before.0, &after.0);
            }
        }

        Ok(match nodes.len() {
            0 => ProcessedBlock::Empty,
            1 => ProcessedBlock::Statement(nodes.first().unwrap().0),
            _ => ProcessedBlock::Boundaries {
                start_node: nodes.first().unwrap().0,
                end_node: nodes.last().unwrap().0,
            },
        })
    }

    /// Extract decision node from AST Node enum given it's ID
    fn get_condition(&mut self, decision_id: u32) -> Result<condition::Reader<'a>> {
        let node = self.get_node(decision_id)?;
        match node.get_contents().which()? {
            ast_node::contents::Condition(condition) => Ok(condition?),
            _ => bail!(MethodProcessingError::UsedNonDecisionNodeInDecision),
        }
    }

    /// Process a binary decision linking it to the appropriate targets
    ///
    /// We must process the right side first as we know that the element on the far right of the
    /// decision tree must target the current true target, and the current false target.
    ///
    /// The left child of an And node should target the right side if it is true, and the current
    /// false target if it is false mimicing short circuiting where the And operator can skip
    /// the right if the left is false and must check the right if the left is true.
    ///
    /// The left child of an Or node should target the current true target if it is true, and the
    /// current right side if it is false mimicing short circuiting where the Or operator
    /// can skip the right if the left is true and must check the right if the left is false
    fn process_binary_condition(
        &mut self,
        left: u32,
        right: u32,
        decision_type: BinaryDecision,
        target: &DecisionTarget,
    ) -> Result<Vec<NodeIndex>> {
        let right = self.get_condition(right)?;
        let mut right = self.process_condition(right, target)?;
        let left = self.get_condition(left)?;
        let mut left = self.process_condition(
            left,
            &DecisionTarget {
                true_target: match decision_type {
                    BinaryDecision::And => right.first().unwrap(),
                    BinaryDecision::Or => target.true_target,
                },
                false_target: match decision_type {
                    BinaryDecision::And => target.false_target,
                    BinaryDecision::Or => right.first().unwrap(),
                },
            },
        )?;
        left.append(&mut right);
        Ok(left)
    }

    /// Process a Decision Node either calling process binary decision if it's an And or Or, or
    /// linking it to the targets if its a unit. If it is an empty decision, simply return an empty
    /// vectors
    fn process_condition(
        &mut self,
        condition: condition::Reader<'a>,
        target: &DecisionTarget,
    ) -> Result<Vec<NodeIndex>> {
        match condition.which()? {
            condition::And(and) => self.process_binary_condition(
                and.get_left(),
                and.get_right(),
                BinaryDecision::And,
                target,
            ),
            condition::Or(or) => self.process_binary_condition(
                or.get_left(),
                or.get_right(),
                BinaryDecision::Or,
                target,
            ),
            condition::Unit(expr) => {
                let dec = self.add_decision_node(expr?);
                self.add_decision_edge(&dec, target.true_target, true);
                self.add_decision_edge(&dec, target.false_target, false);
                Ok(vec![dec])
            }
            condition::Empty(()) => Ok(Vec::new()),
        }
    }

    fn connect_decision_block_and_next(
        &mut self,
        decision: condition::Reader<'a>,
        next: &NodeIndex,
        block: &ProcessedBlock,
    ) -> Result<Vec<NodeIndex>> {
        match block {
            // If the processed block is empty, we want the decision node to target
            // the next node in both cases
            ProcessedBlock::Empty => self.process_condition(
                decision,
                &DecisionTarget {
                    true_target: next,
                    false_target: next,
                },
            ),
            // If the processed block only has a single statement we want to have the Decision
            // node target the statement for the true case and the next node for the false case
            ProcessedBlock::Statement(stmt) => self.process_condition(
                decision,
                &DecisionTarget {
                    true_target: stmt,
                    false_target: next,
                },
            ),
            // Finally, if the processed block has boundaries, we want to have the Decision node
            // target the start node of the block for the true case, and the next node for the
            // false case.
            ProcessedBlock::Boundaries { start_node, .. } => self.process_condition(
                decision,
                &DecisionTarget {
                    true_target: start_node,
                    false_target: next,
                },
            ),
        }
    }

    /// Connect a block to a node and return the node that should be targeted
    fn connect_block_to_node<'b>(
        &mut self,
        block: &'b ProcessedBlock,
        node: &'b NodeIndex,
    ) -> &'b NodeIndex {
        match block {
            ProcessedBlock::Empty => node,
            ProcessedBlock::Statement(stmt) => {
                self.add_statement_edge(stmt, node);
                stmt
            }
            ProcessedBlock::Boundaries {
                start_node,
                end_node,
            } => {
                self.add_statement_edge(end_node, node);
                start_node
            }
        }
    }

    fn process_function(
        &mut self,
        ctx: ProcessorContext,
        function_block: function_block::Reader<'a>,
    ) -> Result<NodeType> {
        let function_name = match function_block.get_name().which()? {
            function_block::name::Some(name) => Some(name?),
            function_block::name::None(()) => None,
        };
        let function_source_node = self.method_graph.add_node(CfgNode {
            label: None,
            node_type: CfgNodeType::Source {
                name: function_name,
            },
        });
        let function_sink_node = self.method_graph.add_node(CfgNode {
            label: None,
            node_type: CfgNodeType::Sink {
                name: function_name,
            },
        });

        let block_ctx = ProcessorContext {
            sink_node: Some(&function_sink_node),
            most_recent_node: Some(function_sink_node),
            ..ctx
        };

        let end_node = self.process_block(block_ctx, function_block.get_block()?)?;
        match end_node {
            ProcessedBlock::Empty => {}
            ProcessedBlock::Statement(start_node)
            | ProcessedBlock::Boundaries { start_node, .. } => {
                self.method_graph
                    .add_edge(function_source_node, start_node, CfgEdge::Statement);
            }
        };
        self.process_labels(function_source_node)?;
        Ok(NodeType::Root)
    }

    fn start_of_block(block: ProcessedBlock) -> Option<NodeIndex> {
        match &block {
            ProcessedBlock::Empty => None,
            ProcessedBlock::Statement(stmt) => Some(*stmt),
            ProcessedBlock::Boundaries { start_node, .. } => Some(*start_node),
        }
    }

    fn end_of_block(block: ProcessedBlock) -> Option<NodeIndex> {
        match &block {
            ProcessedBlock::Empty => None,
            ProcessedBlock::Statement(stmt) => Some(*stmt),
            ProcessedBlock::Boundaries { end_node, .. } => Some(*end_node),
        }
    }

    fn process_loop<'b>(
        &mut self,
        ctx: ProcessorContext<'a, 'b>,
        loop_block: loop_block::Reader<'a>,
    ) -> Result<NodeType> {
        if !loop_block.get_first_iteration_condition_check()
            && (loop_block.get_update()?.len() > 0 || loop_block.get_initialization()?.len() > 0)
        {
            Err(MethodProcessingError::TriedToCreateDoForLoop)?
        }

        let initialization_nodes: Vec<NodeIndex> = self.add_and_link_statement_nodes(
            &loop_block
                .get_initialization()?
                .iter()
                .map(|i| Ok((None, i?)))
                .collect::<Result<Vec<(Option<&str>, &str)>>>()?,
        );

        let update_nodes: Vec<NodeIndex> = self.add_and_link_statement_nodes(
            &loop_block
                .get_update()?
                .iter()
                .map(|i| Ok((None, i?)))
                .collect::<Result<Vec<(Option<&str>, &str)>>>()?,
        );

        let next_node = ctx.most_recent_node.unwrap();

        let continue_node = self.method_graph.add_node(CfgNode {
            label: None,
            node_type: CfgNodeType::Sink { name: None },
        });
        let break_node = self.method_graph.add_node(CfgNode {
            label: None,
            node_type: CfgNodeType::Sink { name: None },
        });

        let block_context = ProcessorContext {
            most_recent_node: None,
            nearest_continueable_node: Some(&continue_node),
            nearest_breakable_node: Some(&break_node),
            ..ctx
        };

        let block_nodes = self.process_block(block_context, loop_block.get_block()?)?;

        let decision_nodes = self.connect_decision_block_and_next(
            loop_block.get_condition()?,
            &next_node,
            &block_nodes,
        )?;

        if !update_nodes.is_empty() {
            self.connect_block_to_node(&block_nodes, update_nodes.first().unwrap());
        } else if !decision_nodes.is_empty() {
            self.connect_block_to_node(&block_nodes, decision_nodes.first().unwrap());
        } else {
            match &block_nodes {
                ProcessedBlock::Empty => {}
                ProcessedBlock::Statement(stmt) => {
                    self.connect_block_to_node(&block_nodes, stmt);
                }
                ProcessedBlock::Boundaries { start_node, .. } => {
                    self.connect_block_to_node(&block_nodes, start_node);
                }
            };
        };

        let continue_target = if !update_nodes.is_empty() {
            *update_nodes.first().unwrap()
        } else if !decision_nodes.is_empty() {
            *decision_nodes.first().unwrap()
        } else if let Some(block) = Self::start_of_block(block_nodes) {
            block
        } else {
            unreachable!("Somehow someone has a continue statement inside of an empty block???")
        };

        let break_target = ctx.most_recent_node.unwrap();

        let continue_edges: Vec<(NodeIndex, CfgEdge)> = self
            .method_graph
            .edges_directed(continue_node, Direction::Incoming)
            .map(|e| (e.source(), *e.weight()))
            .collect();

        for (source, weight) in continue_edges {
            self.method_graph.add_edge(source, continue_target, weight);
        }

        self.method_graph.remove_node(continue_node);

        let break_edges: Vec<(NodeIndex, CfgEdge)> = self
            .method_graph
            .edges_directed(break_node, Direction::Incoming)
            .map(|e| (e.source(), *e.weight()))
            .collect();

        for (source, weight) in break_edges {
            self.method_graph.add_edge(source, break_target, weight);
        }

        self.method_graph.remove_node(break_node);

        let function_node = match (
            !initialization_nodes.is_empty(),
            !decision_nodes.is_empty(),
            loop_block.get_first_iteration_condition_check(),
            Self::start_of_block(block_nodes),
        ) {
            (true, true, true, _) => {
                let init_end = initialization_nodes.last().unwrap();
                let dec_start = decision_nodes.first().unwrap();
                self.add_statement_edge(init_end, dec_start);
                NodeType::Child(*dec_start)
            }
            (true, false, _, Some(block_start)) => {
                let init_end = initialization_nodes.last().unwrap();
                self.add_statement_edge(init_end, &block_start);
                NodeType::Child(*initialization_nodes.first().unwrap())
            }
            (true, false, _, None) => {
                let init_end = initialization_nodes.last().unwrap();
                self.add_statement_edge(init_end, &next_node);
                NodeType::Child(*initialization_nodes.first().unwrap())
            }
            (false, false, _, Some(ni)) | (_, true, false, Some(ni)) => NodeType::Child(ni),
            (false, false, _, _) | (_, true, false, _) => NodeType::Root,
            (false, true, true, _) => NodeType::Child(*decision_nodes.first().unwrap()),
        };

        if let Some(label) = ctx.label {
            let label_node = self.method_graph.add_node(CfgNode {
                label: Some(label),
                node_type: CfgNodeType::Label,
            });

            let continue_target = if !decision_nodes.is_empty() {
                Some(*decision_nodes.first().unwrap())
            } else {
                Self::start_of_block(block_nodes)
            };

            if let Some(ni) = continue_target {
                self.method_graph
                    .add_edge(label_node, ni, CfgEdge::ContinueLabel);
            }

            if let Some(ni) = ctx.most_recent_node {
                self.method_graph
                    .add_edge(label_node, ni, CfgEdge::BreakLabel);
            }

            Ok(NodeType::Child(label_node))
        } else {
            Ok(function_node)
        }
    }

    fn process_try(
        &mut self,
        ctx: ProcessorContext,
        try_block: try_block::Reader<'a>,
    ) -> Result<NodeType> {
        let mut exception_types: Vec<(&'a str, NodeIndex)> = Vec::new();

        let most_recent_node = ctx.most_recent_node.unwrap();
        for catch in try_block
            .get_catches()?
            .iter()
            .rev()
            .map(|c| {
                if let ast_node::contents::CatchBlock(Ok(c)) =
                    self.get_node(c)?.get_contents().which()?
                {
                    Ok(Some(c))
                } else {
                    Ok(None)
                }
            })
            .filter_map_ok(|o| o)
            .collect::<Result<Vec<catch_block::Reader>>>()?
        {
            let catch_block = self.process_block(ctx, catch.get_block()?)?;
            for exception_type in catch.get_exception_types()? {
                exception_types.push((
                    exception_type?,
                    if let Some(ni) = Self::start_of_block(catch_block) {
                        ni
                    } else {
                        most_recent_node
                    },
                ));
            }
        }

        let try_context = ProcessorContext {
            exception_nodes: &exception_types.into_iter().collect(),
            ..ctx
        };

        let block = self.process_block(try_context, try_block.get_block()?)?;
        if let Some(ni) = Self::end_of_block(block) {
            self.add_statement_edge(&ni, &most_recent_node);
            Ok(NodeType::Child(Self::start_of_block(block).unwrap()))
        } else {
            Ok(NodeType::Child(ctx.most_recent_node.unwrap()))
        }
    }

    fn process_throw(
        &mut self,
        ctx: ProcessorContext,
        throw_statement: throw_statement::Reader<'a>,
    ) -> Result<NodeType> {
        if throw_statement.get_exception()?.len() == 0 {
            bail!(MethodProcessingError::NotSupported(
                "Throw statements must have at least one exception associated with them"
                    .to_string(),
            ))
        } else {
            let (target_node, conveying_exception) = throw_statement
                .get_exception()?
                .iter()
                .find_map(|exception_type| match exception_type {
                    Ok(e) => ctx.exception_nodes.get(e).map(|n| Ok((n, e))),
                    Err(e) => Some(Err(e)),
                })
                .unwrap_or(Ok((
                    ctx.sink_node.unwrap(),
                    throw_statement.get_exception()?.get(0)?,
                )))?;

            let source_node = self.add_exception_node(throw_statement.get_statement()?);

            self.add_exception_edge(&source_node, target_node, conveying_exception);

            Ok(NodeType::Child(source_node))
        }
    }

    fn process_statement(
        &mut self,
        ctx: ProcessorContext<'a, '_>,
        statement: statement::Reader<'a>,
    ) -> Result<NodeType> {
        Ok(NodeType::Child(
            self.add_statement_node(ctx.label, statement.get_code()?),
        ))
    }

    fn process_yield(
        &mut self,
        _ctx: ProcessorContext,
        _yield_statement: yield_statement::Reader<'a>,
    ) -> Result<NodeType> {
        bail!(MethodProcessingError::NotImplemented(
            "Yield statement is not yet implemented".to_string(),
        ))
    }

    fn process_break(
        &mut self,
        ctx: ProcessorContext<'a, '_>,
        break_statement: break_statement::Reader<'a>,
    ) -> Result<NodeType> {
        let label = match break_statement.get_label().which()? {
            break_statement::label::Some(label) => Some(label?),
            break_statement::label::None(()) => None,
        };
        let source = self.add_control_node(label, ControlNodeType::Break);
        match (label, ctx.nearest_breakable_node) {
            (None, Some(target)) => {
                self.add_statement_edge(&source, target);
                Ok(NodeType::Child(source))
            }
            _ => Ok(NodeType::Child(source)),
        }
    }

    fn process_continue(
        &mut self,
        ctx: ProcessorContext,
        continue_statement: continue_statement::Reader<'a>,
    ) -> Result<NodeType> {
        let label = match continue_statement.get_label().which()? {
            continue_statement::label::Some(label) => Some(label?),
            continue_statement::label::None(()) => None,
        };
        let source = self.add_control_node(label, ControlNodeType::Continue);
        match (label, ctx.nearest_continueable_node) {
            (None, Some(target)) => {
                self.add_statement_edge(&source, target);
                Ok(NodeType::Child(source))
            }
            _ => Ok(NodeType::Child(source)),
        }
    }

    fn process_return(
        &mut self,
        ctx: ProcessorContext,
        return_statement: return_statement::Reader<'a>,
    ) -> Result<NodeType> {
        let expression = match return_statement.get_expression().which()? {
            return_statement::expression::Some(expr) => Some(expr?),
            return_statement::expression::None(()) => None,
        };
        let source = self.add_control_node(expression, ControlNodeType::Return);
        match (expression, ctx.sink_node) {
            (None, Some(target)) => {
                self.add_statement_edge(&source, target);
                Ok(NodeType::Child(source))
            }
            _ => Ok(NodeType::Child(source)),
        }
    }

    fn process_decision_block(
        &mut self,
        ctx: ProcessorContext,
        decision_block: decision_block::Reader<'a>,
    ) -> Result<NodeType> {
        let next_node = ctx.most_recent_node.unwrap();

        let block_context = ProcessorContext { ..ctx };

        let block_nodes = self.process_block(block_context, decision_block.get_block()?)?;

        let decision_nodes = self.connect_decision_block_and_next(
            decision_block.get_condition()?,
            &next_node,
            &block_nodes,
        )?;

        if !decision_nodes.is_empty() {
            Ok(NodeType::Child(*decision_nodes.first().unwrap()))
        } else if let Some(ni) = Self::start_of_block(block_nodes) {
            Ok(NodeType::Child(ni))
        } else {
            Ok(NodeType::Root)
        }
    }
}
