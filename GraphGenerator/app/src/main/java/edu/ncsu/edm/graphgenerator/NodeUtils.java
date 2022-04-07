package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.ast.stmt.Statement;
import org.jgrapht.Graph;

import java.util.*;
import java.util.function.Predicate;

public class NodeUtils {
    public static Statement getParentStatement(Statement stmt) {
        return stmt.findAncestor(Statement.class, s -> s.isDoStmt() || s.isForEachStmt() ||
               s.isForStmt() || s.isIfStmt() ||
               s.isWhileStmt()
        ).get();
    }

    private static FlowNode getNearestAncestorByConditional(Graph<FlowNode, FlowEdge> graph,
                                                            FlowNode start, Predicate<FlowNode> condition,
                                                            String condString) {
        Stack<FlowEdge> ancestorEdges = new Stack<>();
        ancestorEdges.addAll(graph.incomingEdgesOf(start));
        Set<FlowEdge> seenEdges = new HashSet<>();

        while (!ancestorEdges.isEmpty()) {
            FlowEdge current = ancestorEdges.pop();
            FlowNode ancestor = graph.getEdgeSource(current);
            if (condition.test(ancestor)) {
                return ancestor;
            }
            ancestorEdges.addAll(graph.incomingEdgesOf(ancestor).stream().filter(e -> !seenEdges.contains(e)).toList());
            seenEdges.addAll(graph.incomingEdgesOf(ancestor));
        }

        throw new IllegalStateException("You asked for ancestor from a graph that didn't have an ancestor that " +
                "met this condition: " + condString);

    }

    public static FlowNode getNearestContinuableAncestor(Graph<FlowNode, FlowEdge> graph, FlowNode start) {
        return getNearestAncestorByConditional (graph, start, ancestor ->
                ancestor.getNode().isPresent() && ancestor.getNode().get() instanceof Statement ancestorNode &&
                (ancestorNode.isForStmt() || ancestorNode.isForEachStmt() ||
                ancestorNode.isDoStmt() || ancestorNode.isWhileStmt()),
                "can continue to this ancestor"
        );
    }

    private static boolean isBreakableNode(FlowNode node) {
        return node.getNode().isPresent() && node.getNode().get() instanceof Statement ancestorNode &&
                (
                        ancestorNode.isForStmt() ||
                                ancestorNode.isForEachStmt() ||
                                ancestorNode.isDoStmt() ||
                                ancestorNode.isWhileStmt() ||
                                ancestorNode.isSwitchStmt()
                );
    }

    public static FlowNode getNearestBreakableAncestor(Graph<FlowNode, FlowEdge> graph, FlowNode start) {
        return getNearestAncestorByConditional (graph, start, NodeUtils::isBreakableNode,
                "Can break to this ancestor"
        );
    }

    public static FlowNode getLabeledAncestor(Graph<FlowNode, FlowEdge> graph, FlowNode start, String label) {
        return getNearestAncestorByConditional(graph, start, ancestor ->
                ancestor.getLabel().isPresent() && ancestor.getLabel().get().equals(label),
                "Has label " + label);
    }

    public static FlowNode getNearestReturnableAncestor(Graph<FlowNode, FlowEdge> graph, FlowNode start) {
        for (FlowNode n: graph.vertexSet()) {
            if (n.getName().isPresent() && n.getName().get().startsWith("after")) {
                return n;
            }
        }

        throw new IllegalStateException("You asked for ancestor from a graph that didn't have an ancestor that " +
                "met this condition: Has a name that starts with 'after'");
    }
}
