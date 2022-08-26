use crate::capnp::readers::*;
use crate::cfg::blocks::ProcessedBlock;
use crate::cfg::cfg_node::{ControlType, LabelEdge, Node, NodeType};
use crate::cfg::decisions::{BinaryDecision, DecisionTarget};
use crate::cfg::errors::LabelProcessingError;
use crate::cfg::{cfg_node, Edge, MethodProcessingError};
use itertools::Itertools;
use log::trace;
use petgraph::prelude::EdgeRef;
use petgraph::stable_graph::{NodeIndex, StableDiGraph};
use petgraph::Direction;
use std::borrow::Borrow;
use std::collections::{HashMap, HashSet};

#[derive(Copy, Clone, Debug)]
struct ProcessorContext<'b> {
    label: Option<NodeIndex>,
    most_recent_node: Option<NodeIndex>,
    nearest_breakable_node: Option<NodeIndex>,
    nearest_continuable_node: Option<NodeIndex>,
    sink_node: Option<NodeIndex>,
    exception_nodes: &'b HashMap<&'b str, NodeIndex>,
}

enum ProcessedNode {
    Child(NodeIndex),
    Root,
}

pub enum Exits {
    Yes,
    No,
}

pub struct AstProcessor<'a> {
    pub method_graph: StableDiGraph<Node<'a>, Edge<'a>>,
}
impl<'a> AstProcessor<'a> {
    // Public Methods

    /// A method for them to interact with the API
    pub fn process_method(
        nodes: ast_node::Reader<'a>,
    ) -> Result<StableDiGraph<Node<'a>, Edge<'a>>, MethodProcessingError> {
        if let ast_node::contents::FunctionBlock(Ok(f)) = nodes.get_contents().which()? {
            if f.get_name().has_some() {
                let mut ast_processor = AstProcessor {
                    method_graph: StableDiGraph::new(),
                };

                let base_context = ProcessorContext {
                    label: None,
                    most_recent_node: None,
                    nearest_breakable_node: None,
                    nearest_continuable_node: None,
                    sink_node: None,
                    exception_nodes: &HashMap::new(),
                };

                ast_processor.process_function(base_context, f)?;
                Ok(ast_processor.method_graph)
            } else {
                Err(MethodProcessingError::TopLevelAnonMethod)?
            }
        } else {
            Err(MethodProcessingError::NotAMethod)?
        }
    }

    /// Step through the graph rerouting all labeled connections to the appropriate start point.
    fn process_labels(&mut self, start_node: NodeIndex) -> Result<(), LabelProcessingError> {
        let labels_to_delete =
            self.process_labels_recurse(start_node, &HashMap::new(), &mut HashSet::new())?;

        // Delete all label nodes and redirect the incoming nodes to the next node
        for node in labels_to_delete.into_iter() {
            let target = self
                .method_graph
                .edges(node)
                .find(|e| e.weight().is_label_next())
                .unwrap()
                .target();

            for (source, weight) in self.get_incoming_edges(node) {
                trace!(
                    "Remapping edge from dummy node: {:#?} -> {:#?} ({:#?})",
                    source,
                    target,
                    weight
                );
                self.method_graph.add_edge(source, target, weight);
            }
            self.method_graph.remove_node(node);
        }

        Ok(())
    }

    fn process_labels_recurse(
        &mut self,
        current_node: NodeIndex,
        labels: &HashMap<&str, NodeIndex>,
        visited: &mut HashSet<NodeIndex>,
    ) -> Result<Vec<NodeIndex>, LabelProcessingError> {
        if visited.contains(&current_node) {
            return Ok(Vec::new());
        }

        visited.insert(current_node);

        let mut new_labels = labels.clone();
        let mut labels_to_delete = Vec::new();

        let current = &self.method_graph[current_node];
        match &current.node_type {
            NodeType::ControlNode(control_type) => {
                if let Some(label) = current.label {
                    if let Some(label_node) = labels.get(label) {
                        match control_type {
                            ControlType::Break => {
                                let break_edge = self
                                    .method_graph
                                    .edges(*label_node)
                                    .find(|e| e.weight().is_break());
                                if let Some(target) = break_edge.map(|e| e.target()) {
                                    self.add_statement_edge(current_node, target);
                                }
                            }
                            ControlType::_Yield => Err(LabelProcessingError::NotImplemented(
                                "Yield statement is not yet implemented".to_string(),
                            ))?,
                            ControlType::Return => Err(LabelProcessingError::ReturnToLabel)?,
                            ControlType::Continue => {
                                let continue_edge = self
                                    .method_graph
                                    .edges(*label_node)
                                    .find(|e| e.weight().is_continue());
                                if let Some(target) = continue_edge.map(|e| e.target()) {
                                    self.add_statement_edge(current_node, target);
                                }
                            }
                        };
                    }
                }
            }
            NodeType::Label => {
                if let Some(label) = current.label {
                    new_labels.insert(label, current_node);
                    labels_to_delete.push(current_node);
                }
            }
            _ => {}
        }

        for neighbor in self
            .method_graph
            .neighbors_directed(current_node, Direction::Outgoing)
            .collect_vec()
        {
            let mut neighbor_labels_to_delete =
                self.process_labels_recurse(neighbor, &new_labels, visited)?;
            labels_to_delete.append(&mut neighbor_labels_to_delete);
        }
        Ok(labels_to_delete)
    }

    // Helper functions

    /// A function to split nodes to their respective handlers
    ///
    /// The following handle other types of nodes as well
    /// LoopBlock - Decision nodes
    /// DecisionBlock - Decision nodes
    /// TryBlock - CatchBlock nodes
    fn process_node<'b>(
        &mut self,
        node_ctx: ProcessorContext<'b>,
        node: ast_node::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        let dummy_break = self.method_graph.add_node(Node {
            label: None,
            node_type: NodeType::Sink {
                name: Some("Dummy If Break Node"),
            },
        });

        let ctx = ProcessorContext {
            label: match node.get_label().which()? {
                ast_node::label::Some(label) => {
                    let label = label?;
                    if label.is_empty() {
                        Err(MethodProcessingError::LabelProcessing {
                            error: LabelProcessingError::EmptySomeLabel,
                        })?;
                    }

                    trace!("process node label: {:#?}", label);
                    Some(self.method_graph.add_node(Node {
                        label: Some(label),
                        node_type: NodeType::Label,
                    }))
                }
                ast_node::label::None(()) => None,
            },
            nearest_breakable_node: if node.get_breakable() {
                Some(dummy_break)
            } else {
                node_ctx.nearest_breakable_node
            },
            ..node_ctx
        };

        for lambda_func in node.get_lambda_functions()? {
            self.process_function(ctx, lambda_func)?;
        }

        let content_node = match node.get_contents().which()? {
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
            ast_node::contents::Block(b) => if let Some(block) = Self::start_of_block(self.process_block(ctx, b?)?) {
                Ok(ProcessedNode::Child(block))
            } else {
                Ok(ProcessedNode::Root)
            },
            _ => Err(MethodProcessingError::NotSupported("You have a node in your blocks that can only be unpacked in a specific type of node.".to_string()))
        }?;

        let next_node = node_ctx.most_recent_node.unwrap();
        for (source, weight) in self.get_incoming_edges(dummy_break) {
            self.method_graph.add_edge(source, next_node, weight);
        }

        self.method_graph.remove_node(dummy_break);

        if let (Some(label_node), ProcessedNode::Child(content_index)) = (ctx.label, &content_node)
        {
            self.method_graph
                .add_edge(label_node, *content_index, Edge::Label(LabelEdge::Next));
            self.method_graph.add_edge(
                label_node,
                node_ctx.most_recent_node.unwrap(),
                Edge::Label(LabelEdge::Break),
            );
            Ok(ProcessedNode::Child(label_node))
        } else {
            Ok(content_node)
        }
    }

    // Add node helper methods

    /// Add a decision node with the given decision expression to the graph
    fn add_decision_node(&mut self, decision: &'a str) -> NodeIndex {
        self.method_graph.add_node(Node {
            label: None,
            node_type: NodeType::Decision { decision },
        })
    }

    /// Add a statement node with the given statement to the graph
    fn add_statement_node(&mut self, stmt: &'a str) -> NodeIndex {
        self.method_graph.add_node(Node {
            label: None,
            node_type: NodeType::Statement { statement: stmt },
        })
    }

    /// Add a list of statement nodes with the given statement values to the graph linking them
    /// one to the next in order
    fn add_and_link_statement_nodes<'b>(&mut self, statements: &'b [&'a str]) -> Vec<NodeIndex> {
        let nodes: Vec<NodeIndex> = statements
            .iter()
            .map(|s| self.add_statement_node(s))
            .collect();

        for (start, end) in nodes.iter().tuple_windows() {
            self.add_statement_edge(*start, *end);
        }

        nodes
    }

    fn add_exception_node(&mut self, stmt: &'a str) -> NodeIndex {
        self.method_graph.add_node(Node {
            label: None,
            node_type: NodeType::Exception { statement: stmt },
        })
    }

    /// Add a statement node with the given statement to the graph
    fn add_control_node(&mut self, label: Option<&'a str>, node_type: ControlType) -> NodeIndex {
        self.method_graph.add_node(Node {
            label,
            node_type: NodeType::ControlNode(node_type),
        })
    }

    // Add edge helper methods

    /// Add a chosen `direction` decision edge to the graph connecting the `start` node
    /// and `end` node
    fn add_decision_edge(
        &mut self,
        start: NodeIndex,
        end: NodeIndex,
        direction: cfg_node::Direction,
    ) -> petgraph::stable_graph::EdgeIndex {
        trace!(
            "Adding decision edge: {:#?} -> {:#?} ({:#?})",
            start,
            end,
            direction
        );
        self.method_graph
            .add_edge(start, end, Edge::Decision(direction))
    }

    /// Add a statement edge to the graph connecting the `start` node and `end` node
    fn add_statement_edge(
        &mut self,
        start: NodeIndex,
        end: NodeIndex,
    ) -> petgraph::stable_graph::EdgeIndex {
        trace!("Adding edge: {:#?} -> {:#?}", start, end);
        self.method_graph.add_edge(start, end, Edge::Statement)
    }

    fn add_exception_edge(
        &mut self,
        start: NodeIndex,
        end: NodeIndex,
        exception_type: &'a str,
    ) -> petgraph::stable_graph::EdgeIndex {
        trace!(
            "Adding exception edge: {:#?} -> {:#?} ({:#?})",
            start,
            end,
            exception_type
        );
        self.method_graph.add_edge(
            start,
            end,
            Edge::Exception {
                exception: exception_type.borrow(),
            },
        )
    }

    fn exits(node: ast_node::Reader) -> Result<Exits, MethodProcessingError> {
        match node.get_contents().which()? {
            ast_node::contents::ThrowStatement(_)
            | ast_node::contents::YieldStatement(_)
            | ast_node::contents::BreakStatement(_)
            | ast_node::contents::ContinueStatement(_)
            | ast_node::contents::ReturnStatement(_)
            | ast_node::contents::DecisionBlock(_)
            | ast_node::contents::LoopBlock(_) => Ok(Exits::Yes),
            _ => Ok(Exits::No),
        }
    }

    fn process_block<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        block: block::Reader<'a>,
    ) -> Result<ProcessedBlock, MethodProcessingError> {
        let mut block_context = ctx;
        let nodes: Vec<(NodeIndex, Exits)> = block
            .get_statements()?
            .iter()
            .rev()
            .map(|s| {
                let result = self.process_node(block_context, s)?;
                match result {
                    ProcessedNode::Child(node) => {
                        block_context = ProcessorContext {
                            most_recent_node: Some(node),
                            ..block_context
                        };
                        Ok(Some((node, AstProcessor::exits(s)?)))
                    }
                    ProcessedNode::Root => Ok(None),
                }
            })
            .filter_map_ok(|opt| opt)
            .collect::<Result<Vec<(NodeIndex, Exits)>, MethodProcessingError>>()?;

        for (before, after) in nodes
            .iter()
            .rev()
            .tuple_windows::<(&(NodeIndex, Exits), &(NodeIndex, Exits))>()
        {
            if let Exits::No = before.1 {
                self.add_statement_edge(before.0, after.0);
            }
        }

        let block = match nodes.len() {
            0 => ProcessedBlock::Empty,
            1 => ProcessedBlock::Statement(nodes.first().unwrap().0),
            _ => ProcessedBlock::Boundaries {
                start_node: nodes.last().unwrap().0,
                _end_node: nodes.first().unwrap().0,
            },
        };

        if let Some((end_node, end_exits)) = nodes.first() {
            if let (Exits::No, Some(most_recent_node)) = (end_exits, ctx.most_recent_node) {
                self.add_statement_edge(*end_node, most_recent_node);
            }
        }

        Ok(block)
    }

    /// Process a binary decision linking it to the appropriate targets
    ///
    /// We must process the right side first as we know that the element on the far right of the
    /// decision tree must target the current true target, and the current false target.
    ///
    /// The left child of an And node should target the right side if it is true, and the current
    /// false target if it is false mimicking short circuiting where the And operator can skip
    /// the right if the left is false and must check the right if the left is true.
    ///
    /// The left child of an Or node should target the current true target if it is true, and the
    /// current right side if it is false mimicking short circuiting where the Or operator
    /// can skip the right if the left is true and must check the right if the left is false
    fn process_binary_condition(
        &mut self,
        left: condition::Reader<'a>,
        right: condition::Reader<'a>,
        decision_type: BinaryDecision,
        target: &DecisionTarget,
    ) -> Result<Vec<NodeIndex>, MethodProcessingError> {
        let mut right = self.process_condition(right, target)?;
        let mut left = self.process_condition(
            left,
            &DecisionTarget {
                true_target: match decision_type {
                    BinaryDecision::And => *right.first().unwrap(),
                    BinaryDecision::Or => target.true_target,
                },
                false_target: match decision_type {
                    BinaryDecision::And => target.false_target,
                    BinaryDecision::Or => *right.first().unwrap(),
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
    ) -> Result<Vec<NodeIndex>, MethodProcessingError> {
        match condition.which()? {
            condition::And(and) => self.process_binary_condition(
                and.get_left()?,
                and.get_right()?,
                BinaryDecision::And,
                target,
            ),
            condition::Or(or) => self.process_binary_condition(
                or.get_left()?,
                or.get_right()?,
                BinaryDecision::Or,
                target,
            ),
            condition::Unit(expr) => {
                let dec = self.add_decision_node(expr?);
                self.add_decision_edge(dec, target.true_target, cfg_node::Direction::True);
                self.add_decision_edge(dec, target.false_target, cfg_node::Direction::False);
                Ok(vec![dec])
            }
            condition::Empty(()) => Ok(Vec::new()),
        }
    }

    /// Handles generating a functions CFG with dummy label nodes and then taking the label nodes
    /// and redirecting the incoming edges.sql to a label node to the appropriate location.
    fn process_function<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        function_block: function_block::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        let function_name = match function_block.get_name().which()? {
            function_block::name::Some(name) => Some(name?),
            function_block::name::None(()) => None,
        };
        let function_source_node = self.method_graph.add_node(Node {
            label: None,
            node_type: NodeType::Source {
                name: function_name,
            },
        });
        let function_sink_node = self.method_graph.add_node(Node {
            label: None,
            node_type: NodeType::Sink {
                name: function_name,
            },
        });

        let block_ctx = ProcessorContext {
            sink_node: Some(function_sink_node),
            most_recent_node: Some(function_sink_node),
            ..ctx
        };

        let block = self.process_block(block_ctx, function_block.get_block()?)?;
        match block {
            ProcessedBlock::Empty => {}
            ProcessedBlock::Statement(start_node)
            | ProcessedBlock::Boundaries { start_node, .. } => {
                trace!(
                    "Adding start edge: {:#?} -> {:#?}",
                    function_source_node,
                    start_node
                );
                self.method_graph
                    .add_edge(function_source_node, start_node, Edge::Statement);
            }
        };
        self.process_labels(function_source_node)?;
        Ok(ProcessedNode::Root)
    }

    fn start_of_block(block: ProcessedBlock) -> Option<NodeIndex> {
        match &block {
            ProcessedBlock::Empty => None,
            ProcessedBlock::Statement(stmt) => Some(*stmt),
            ProcessedBlock::Boundaries { start_node, .. } => Some(*start_node),
        }
    }

    fn process_loop<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        loop_block: loop_block::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        // Check for a Do-for loop
        if !loop_block.get_first_iteration_condition_check()
            && (loop_block.get_update()?.len() > 0 || loop_block.get_initialization()?.len() > 0)
        {
            Err(MethodProcessingError::TriedToCreateDoForLoop)?
        }

        // Create and link the nodes for the initialization steps
        let initialization_nodes: Vec<NodeIndex> =
            self.add_loop_statements(loop_block.get_initialization()?)?;

        // Create and link the nodes for the update step
        let update_nodes: Vec<NodeIndex> = self.add_loop_statements(loop_block.get_update()?)?;

        let next_node = ctx.most_recent_node.unwrap();

        // Create a sink break and continue node to allow us to work backwards through a tree
        // and maintain knowledge of where the nodes are trying to break or continue to without
        // needing to actually directly know that right away
        let continue_node = self.method_graph.add_node(Node {
            label: None,
            node_type: NodeType::Sink { name: None },
        });

        let dummy_target = self.method_graph.add_node(Node {
            label: None,
            node_type: NodeType::Sink {
                name: Some("Dummy Target Sink"),
            },
        });

        let block_context = ProcessorContext {
            most_recent_node: Some(dummy_target),
            nearest_continuable_node: Some(continue_node),
            ..ctx
        };

        // Process block
        let block_nodes = self.process_block(block_context, loop_block.get_block()?)?;

        // Process condition
        let decision_true_target = self.get_loop_decision_true_target(&update_nodes, block_nodes);

        let decision_nodes = self.process_condition(
            loop_block.get_condition()?,
            &DecisionTarget {
                true_target: decision_true_target,
                false_target: next_node,
            },
        )?;

        if let (NodeType::Sink { .. }, Some(first)) = (
            &self.method_graph[decision_true_target].node_type,
            decision_nodes.first(),
        ) {
            for (source, weight) in self.get_incoming_edges(decision_true_target) {
                self.method_graph.add_edge(source, *first, weight);
            }

            self.method_graph.remove_node(decision_true_target);
        }

        // Link Init
        if let Some(last) = initialization_nodes.last() {
            let initialization_target =
                Self::get_loop_init_target(&update_nodes, next_node, block_nodes, &decision_nodes);

            self.add_statement_edge(*last, initialization_target);
        }

        // Link Update
        if let (Some(first), Some(last)) = (update_nodes.first(), update_nodes.last()) {
            let update_target = Self::get_loop_update_target(block_nodes, &decision_nodes, *first);

            self.add_statement_edge(*last, update_target);
        }

        // Redirect from the continue dummy node to the appropriate target
        let continue_target = if !update_nodes.is_empty() {
            *update_nodes.first().unwrap()
        } else if !decision_nodes.is_empty() {
            *decision_nodes.first().unwrap()
        } else {
            Self::start_of_block(block_nodes).unwrap()
        };

        let continue_edges: Vec<(NodeIndex, Edge)> = self.get_incoming_edges(continue_node);

        for (source, weight) in continue_edges {
            trace!(
                "Adding continue edge: {:#?} -> {:#?} ({:#?})",
                source,
                continue_target,
                weight
            );
            self.method_graph.add_edge(source, continue_target, weight);
        }

        self.method_graph.remove_node(continue_node);

        // Redirect from the dummy target to the next target
        let dummy_edges = self.get_incoming_edges(dummy_target);
        if !dummy_edges.is_empty() {
            let dummy_target = if let Some(update_first) = update_nodes.first() {
                *update_first
            } else if let Some(dec_first) = decision_nodes.first() {
                *dec_first
            } else {
                Self::start_of_block(block_nodes).unwrap()
            };

            for (source, weight) in dummy_edges {
                self.method_graph.add_edge(source, dummy_target, weight);
            }
        }

        self.method_graph.remove_node(dummy_target);

        // We have processed all the constituent parts at this point
        // Now we need to link every part to the correct part
        //
        // First iteration is true
        // Base connection
        // Init -> Decision T -> Body -> Update -> Decision
        // If body is empty
        // Init -> Decision T -> Update -> Decision
        // If update is empty
        // Init -> Decision T -> Body -> Decision
        // If update and body are empty
        // Init -> Decision T -> Decision
        // If init is empty
        // Decision T -> Body -> Update -> Decision
        // If init and update are empty
        // Decision T -> Body -> Decision
        // If init, update, and body are empty
        // Decision T -> Decision
        // If decision is empty
        // Init -> Body -> Update -> Body
        // If decision and body are empty
        // Init -> Update -> Update
        // If init, update, and decision are empty
        // Body -> Body
        // If init and decision are empty
        // Body -> Update -> Body
        // If decision, init, and body are empty
        // Update -> Update
        //
        // First iteration is false
        // Body -> Decision T -> Body
        // Body -> Body
        let loop_block_root = if let Some(init_first) = initialization_nodes.first() {
            *init_first
        } else if let (Some(dec_first), true) = (
            decision_nodes.first(),
            loop_block.get_first_iteration_condition_check(),
        ) {
            *dec_first
        } else if let (ProcessedBlock::Empty, Some(update_first)) =
            (block_nodes, update_nodes.first())
        {
            *update_first
        } else {
            Self::start_of_block(block_nodes).unwrap()
        };

        if let Some(label) = ctx.label {
            let continue_target = if !decision_nodes.is_empty() {
                Some(*decision_nodes.first().unwrap())
            } else {
                Self::start_of_block(block_nodes)
            };

            if let Some(ni) = continue_target {
                trace!(
                    "Adding labeled continue edge: {:#?} -> {:#?} ({:#?})",
                    label,
                    ni,
                    label
                );
                self.method_graph
                    .add_edge(label, ni, Edge::Label(LabelEdge::Continue));
            }
        }

        Ok(ProcessedNode::Child(loop_block_root))
    }

    /// Gets the correct target for the update step to go to after an update step has been completed
    ///
    /// If there are decision nodes
    /// Update -> Decision
    /// If there are not decision nodes
    /// Update -> Body
    /// If there are no decision or body nodes
    /// Update -> Update
    fn get_loop_update_target(
        block_nodes: ProcessedBlock,
        decision_nodes: &[NodeIndex],
        first: NodeIndex,
    ) -> NodeIndex {
        if let Some(dec_first) = decision_nodes.first() {
            *dec_first
        } else if let ProcessedBlock::Empty = block_nodes {
            first
        } else {
            Self::start_of_block(block_nodes).unwrap()
        }
    }

    /// Gets the correct target node for the initialization step to travel to after it's completed
    ///
    /// If there are decision nodes
    /// Init -> Decision
    /// If there are no decision nodes, but body nodes
    /// Init -> Body
    /// If there are no decision nodes or body nodes
    /// Init -> Update
    /// If there is nothing, assume it's an error but fail gracefully by just routing to the next node
    /// Init -> Next
    fn get_loop_init_target(
        update_nodes: &[NodeIndex],
        next_node: NodeIndex,
        block_nodes: ProcessedBlock,
        decision_nodes: &[NodeIndex],
    ) -> NodeIndex {
        if let Some(dec_first) = decision_nodes.first() {
            *dec_first
        } else if let Some(body_start) = Self::start_of_block(block_nodes) {
            body_start
        } else if let Some(update_start) = update_nodes.first() {
            *update_start
        } else {
            next_node
        }
    }

    /// Gets the correct target for a loop decision based off the current state of the update nodes
    /// and block nodes. Otherwise, it returns a dummy sink node that can be redirected later
    ///
    /// If body is not empty
    /// Decision T -> Body
    /// If body is empty and update is not empty
    /// Decision T -> Update
    /// If update and body are empty
    /// Decision T -> (Dummy sink node which should be redirected to) Decision
    fn get_loop_decision_true_target(
        &mut self,
        update_nodes: &[NodeIndex],
        block_nodes: ProcessedBlock,
    ) -> NodeIndex {
        if let Some(body_start) = Self::start_of_block(block_nodes) {
            body_start
        } else if let Some(update_start) = update_nodes.first() {
            *update_start
        } else {
            self.method_graph.add_node(Node {
                label: None,
                node_type: NodeType::Sink {
                    name: Some("Decision True Target Dummy Node"),
                },
            })
        }
    }

    fn add_loop_statements(
        &mut self,
        statements: capnp::text_list::Reader<'a>,
    ) -> Result<Vec<NodeIndex>, MethodProcessingError> {
        Ok(self.add_and_link_statement_nodes(
            statements
                .iter()
                .map(|i| Ok(i?))
                .collect::<Result<Vec<&str>, MethodProcessingError>>()?
                .borrow(),
        ))
    }

    fn get_incoming_edges(&self, node: NodeIndex) -> Vec<(NodeIndex, Edge<'a>)> {
        self.method_graph
            .edges_directed(node, Direction::Incoming)
            .map(|e| (e.source(), *e.weight()))
            .collect()
    }

    fn process_try<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        try_block: try_block::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        let mut exception_types: Vec<(&'a str, NodeIndex)> = Vec::new();

        let most_recent_node = ctx.most_recent_node.unwrap();

        let finally_block =
            if let try_block::finally::Some(finally) = try_block.get_finally().which()? {
                Some(self.process_block(ctx, finally?)?)
            } else {
                None
            };

        let catch_ctx = ProcessorContext {
            most_recent_node: if let Some(block) = finally_block {
                Self::start_of_block(block)
            } else {
                Some(most_recent_node)
            },
            ..ctx
        };

        for catch in try_block
            .get_catches()?
            .iter()
            .rev()
            .collect::<Vec<catch_block::Reader>>()
        {
            let catch_block = self.process_block(catch_ctx, catch.get_block()?)?;
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

        let exceptions = exception_types.into_iter().collect();
        let try_context = ProcessorContext {
            exception_nodes: &exceptions,
            ..ctx
        };

        let block = self.process_block(try_context, try_block.get_block()?)?;

        Ok(ProcessedNode::Child(
            Self::start_of_block(block).unwrap_or(most_recent_node),
        ))
    }

    fn process_throw<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        throw_statement: throw_statement::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        if throw_statement.get_exception()?.len() == 0 {
            Err(MethodProcessingError::NotSupported(
                "Throw statements must have at least one exception associated with them"
                    .to_string(),
            ))
        } else {
            let (target_node, conveying_exception) = throw_statement
                .get_exception()?
                .iter()
                .find_map(|exception_type| match exception_type {
                    Ok(e) => ctx.exception_nodes.get(e).map(|n| Ok((*n, e))),
                    Err(e) => Some(Err(e)),
                })
                .unwrap_or(Ok((
                    ctx.sink_node.unwrap(),
                    throw_statement.get_exception()?.get(0)?,
                )))?;

            let source_node = self.add_exception_node(throw_statement.get_statement()?);

            self.add_exception_edge(source_node, target_node, conveying_exception);

            Ok(ProcessedNode::Child(source_node))
        }
    }

    fn process_statement<'b>(
        &mut self,
        _ctx: ProcessorContext<'b>,
        statement: statement::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        Ok(ProcessedNode::Child(
            self.add_statement_node(statement.get_code()?),
        ))
    }

    fn process_yield<'b>(
        &mut self,
        _ctx: ProcessorContext<'b>,
        _yield_statement: yield_statement::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        Err(MethodProcessingError::NotImplemented(
            "Yield statement is not yet implemented".to_string(),
        ))
    }

    fn process_break<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        break_statement: break_statement::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        let label = match break_statement.get_label().which()? {
            break_statement::label::Some(label) => Some(label?),
            break_statement::label::None(()) => None,
        };
        let source = self.add_control_node(label, ControlType::Break);
        match (label, ctx.nearest_breakable_node) {
            (None, Some(target)) => {
                self.add_statement_edge(source, target);
                Ok(ProcessedNode::Child(source))
            }
            _ => Ok(ProcessedNode::Child(source)),
        }
    }

    fn process_continue<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        continue_statement: continue_statement::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        let label = match continue_statement.get_label().which()? {
            continue_statement::label::Some(label) => Some(label?),
            continue_statement::label::None(()) => None,
        };
        let source = self.add_control_node(label, ControlType::Continue);
        match (label, ctx.nearest_continuable_node) {
            (None, Some(target)) => {
                self.add_statement_edge(source, target);
                Ok(ProcessedNode::Child(source))
            }
            _ => Ok(ProcessedNode::Child(source)),
        }
    }

    fn process_return<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        return_statement: return_statement::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        let expression = match return_statement.get_expression().which()? {
            return_statement::expression::Some(expr) => Some(expr?),
            return_statement::expression::None(()) => None,
        };
        let source = self.add_control_node(expression, ControlType::Return);
        match ctx.sink_node {
            Some(target) => {
                self.add_statement_edge(source, target);
                Ok(ProcessedNode::Child(source))
            }
            _ => Ok(ProcessedNode::Child(source)),
        }
    }

    fn process_decision_block<'b>(
        &mut self,
        ctx: ProcessorContext<'b>,
        decision_block: decision_block::Reader<'a>,
    ) -> Result<ProcessedNode, MethodProcessingError> {
        trace!("context: {:#?}", ctx);
        let next_node = ctx.most_recent_node.unwrap();

        let false_target =
            if let decision_block::else_::Some(else_node) = decision_block.get_else().which()? {
                match self.process_node(ctx, else_node?)? {
                    ProcessedNode::Child(else_node) => else_node,
                    ProcessedNode::Root => next_node,
                }
            } else {
                next_node
            };

        let block_context = ProcessorContext { ..ctx };

        let block_nodes = self.process_block(block_context, decision_block.get_block()?)?;

        let true_target = if let Some(block_start) = Self::start_of_block(block_nodes) {
            block_start
        } else {
            next_node
        };

        let decision_nodes = self.process_condition(
            decision_block.get_condition()?,
            &DecisionTarget {
                true_target,
                false_target,
            },
        )?;

        if !decision_nodes.is_empty() {
            Ok(ProcessedNode::Child(*decision_nodes.first().unwrap()))
        } else if let Some(ni) = Self::start_of_block(block_nodes) {
            Ok(ProcessedNode::Child(ni))
        } else {
            Ok(ProcessedNode::Root)
        }
    }
}
