package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.ast.stmt.Statement;
import org.jgrapht.Graph;

import java.util.HashSet;
import java.util.Set;
import java.util.Stack;
import java.util.function.Predicate;

public class NodeUtils {
    public static Statement getParentStatement(Statement stmt) {
        return stmt.findAncestor(Statement.class, s -> s.isDoStmt() || s.isForEachStmt() ||
               s.isForStmt() || s.isIfStmt() ||
               s.isWhileStmt()
        ).get();
    }

    private static FlowNode getNearestAncestorByConditional(Graph<FlowNode, FlowEdge> graph, FlowNode start, Predicate<FlowNode> condition) {
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

        throw new IllegalStateException("You asked for a breakable ancestor from a graph that didn't have a breakable ancestor");

    }

    public static FlowNode getNearestContinuableAncestor(Graph<FlowNode, FlowEdge> graph, FlowNode start) {
        return getNearestAncestorByConditional (graph, start, ancestor ->
                ancestor.getNode().isPresent() && ancestor.getNode().get() instanceof Statement ancestorNode &&
                        (ancestorNode.isForStmt() || ancestorNode.isForEachStmt() ||
                                ancestorNode.isDoStmt() || ancestorNode.isWhileStmt()));
    }

    public static FlowNode getNearestBreakableAncestor(Graph<FlowNode, FlowEdge> graph, FlowNode start) {
        return getNearestAncestorByConditional (graph, start, ancestor ->
                ancestor.getNode().isPresent() && ancestor.getNode().get() instanceof Statement ancestorNode &&
                (
                    ancestorNode.isForStmt() ||
                    ancestorNode.isForEachStmt() ||
                    ancestorNode.isDoStmt() ||
                    ancestorNode.isWhileStmt() ||
                    ancestorNode.isSwitchStmt()
                )
        );
    }

    public static FlowNode getLabeledAncestor(Graph<FlowNode, FlowEdge> graph, FlowNode start, String label) {
        Stack<FlowEdge> ancestorEdges = new Stack<>();
        ancestorEdges.addAll(graph.incomingEdgesOf(start));
        Set<FlowEdge> seenEdges = new HashSet<>();

        while (!ancestorEdges.isEmpty()) {
            FlowEdge current = ancestorEdges.pop();
            FlowNode ancestor = graph.getEdgeSource(current);
            if (ancestor.getLabel().isPresent() && ancestor.getLabel().get().equals(label)) {
                return ancestor;
            }
            ancestorEdges.addAll(graph.incomingEdgesOf(ancestor).stream().filter(e -> !seenEdges.contains(e)).toList());
            seenEdges.addAll(graph.incomingEdgesOf(ancestor));
        }

        throw new IllegalStateException("You asked for a labeled ancestor from a graph that didn't have a labeled ancestor labeled " + label);
    }
}
