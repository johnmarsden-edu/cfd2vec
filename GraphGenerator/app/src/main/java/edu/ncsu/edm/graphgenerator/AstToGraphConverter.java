package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.jgrapht.Graph;

import java.util.Set;

public class AstToGraphConverter extends VoidVisitorAdapter<Graph<FlowNode, FlowEdge>> {

    private static class Edge {
        private final FlowNode source;
        private final FlowNode target;
        private final FlowEdge edge;

        public Edge(Statement source, Graph<FlowNode, FlowEdge> g) {
            this.source = new FlowNode(source);
            Set<FlowEdge> edgeSet = g.outgoingEdgesOf(this.source);
            if (edgeSet.size() != 1) {
                throw new IllegalStateException("Somehow we ended up visiting a node that had "+edgeSet.size()+" outgoing edges instead of 1");
            }
            this.edge = edgeSet.stream().findFirst().get();
            this.target = g.getEdgeTarget(this.edge);
        }

        public FlowEdge getEdge() {
            return edge;
        }

        public FlowNode getSource() {
            return source;
        }

        public FlowNode getTarget() {
            return target;
        }
    }

    @Override
    public void visit(MethodDeclaration methodDeclaration, Graph<FlowNode, FlowEdge> g) {
        FlowNode callNode = new FlowNode("before " + methodDeclaration.getNameAsString());
        FlowNode finishNode = new FlowNode("after " + methodDeclaration.getNameAsString());
        g.addVertex(callNode);
        g.addVertex(finishNode);
        FlowNode mdNode = new FlowNode(methodDeclaration);
        g.addVertex(mdNode);
        g.addEdge(callNode, mdNode);
        if (methodDeclaration.getBody().isEmpty()) {
            throw new IllegalStateException("The Method Declaration should never be empty");
        }
        FlowNode methodBody = new FlowNode(methodDeclaration.getBody().get());
        g.addVertex(methodBody);
        g.addEdge(mdNode, methodBody);
        g.addEdge(methodBody, finishNode);
        super.visit(methodDeclaration, g);
    }

//    @Override
//    public void visit(ForEachStmt forEachStmt, Graph<FlowNode, FlowEdge> g) {
//        Edge edge = new Edge(forEachStmt, g);
//        FlowNode finalTarget = edge.getSource();
//        g.removeEdge(edge.getEdge());
//        Expression expr = StaticJavaParser.parseExpression("currIndex == " + forEachStmt.getIterable().toString() + ".size()");
//        g.addEdge(edge.getSource(), edge.getTarget(), new FlowEdge(expr));
//        if (!forEachStmt.hasEmptyBody()) {
//
//            Statement bodyStmt = forEachStmt.getBody();
//            FlowNode target = new FlowNode(bodyStmt);
//            expr = StaticJavaParser.parseExpression("currIndex < " + forEachStmt.getIterable().toString() + ".size()");
//            g.addEdge(edge.getSource(), target, new FlowEdge(expr));
//            FlowNode parent = target;
//
//            if (bodyStmt.isBlockStmt()) {
//                for (Statement stmt : bodyStmt.asBlockStmt().getStatements()) {
//                    target = new FlowNode(stmt);
//                    g.addVertex(target);
//                    g.addEdge(parent, target);
//                    parent = target;
//                }
//            }
//            g.addEdge(parent, finalTarget);
//        }
//        System.out.println("For each statement");
//        super.visit(forEachStmt, g);
//    }
//
//    @Override
//    public void visit(ForStmt forStmt, Graph<FlowNode, FlowEdge> g) {
//        FlowNode parent = new FlowNode(forStmt);
//        Set<FlowEdge> edgeSet = g.outgoingEdgesOf(parent);
//        if (edgeSet.size() != 1) {
//            throw new IllegalStateException("Somehow we ended up visiting a node that had more than one outgoing edge already");
//        }
//
//        FlowEdge outgoingEdge = edgeSet.stream().findFirst().get();
//        FlowNode finalTarget = parent;
//        FlowNode target = g.getEdgeTarget(outgoingEdge);
//
//        if (forStmt.getCompare().isPresent()) {
//            g.removeEdge(outgoingEdge);
//            Expression expr = StaticJavaParser.parseExpression("!(" + forStmt.getCompare().get() + ")");
//            g.addEdge(parent, target, new FlowEdge(expr));
//        }
//
//        if (!forStmt.hasEmptyBody()) {
//            target = new FlowNode(forStmt.getBody().asBlockStmt().stream().findFirst().get());
//
//            if (forStmt.getCompare().isPresent()) {
//                g.addEdge(parent, target, new FlowEdge(forStmt.getCompare().get()));
//            } else {
//                g.addEdge(parent, target);
//            }
//            parent = target;
//            for (Statement stmt : forStmt.getBody().asBlockStmt().getStatements().stream().skip(1).toList()) {
//                target = new FlowNode(stmt);
//                g.addEdge(parent, target);
//                parent = target;
//            }
//            g.addEdge(parent, finalTarget);
//        }
//        super.visit(forStmt, g);
//    }

    @Override
    public void visit(IfStmt ifStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(ifStmt, g);
        FlowNode parent = edge.getSource();
        FlowNode target;

        if (ifStmt.getElseStmt().isPresent()) {
            target = new FlowNode(ifStmt.getElseStmt().get());
            g.addVertex(target);
        } else {
            target = edge.getTarget();
        }

        g.removeEdge(edge.getEdge());
        Expression expr = StaticJavaParser.parseExpression("!(" + ifStmt.getCondition() + ")");
        g.addEdge(parent, target, new FlowEdge(expr));

        target = new FlowNode(ifStmt.getThenStmt());
        g.addVertex(target);
        g.addEdge(parent, target, new FlowEdge(ifStmt.getCondition()));
        g.addEdge(target, edge.getTarget());

        if (ifStmt.getElseStmt().isPresent()) {
            g.addEdge(new FlowNode(ifStmt.getElseStmt().get()), edge.getTarget());
        }
        super.visit(ifStmt, g);
    }

    @Override
    public void visit(BlockStmt blockStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(blockStmt, g);
        FlowNode parent = edge.getSource();
        g.removeEdge(edge.getEdge());
        for (Statement stmt : blockStmt.getStatements()) {
            FlowNode target = new FlowNode(stmt);
            g.addVertex(target);
            g.addEdge(parent, target);
            parent = target;
        }
        g.addEdge(parent, edge.getTarget());
        super.visit(blockStmt, g);
    }

    // @Override
    // public void visit(DoStmt doWhileStmt, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Do-While Statement");
    //     super.visit(doWhileStmt, g);
    // }

    // @Override
    // public void visit(WhileStmt whileStmt, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("While Statement");
    //     super.visit(whileStmt, g);
    // }

    // @Override
    // public void visit(ConditionalExpr ternaryExpr, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Ternary Expression");
    //     super.visit(ternaryExpr, g);
    // }

    // @Override
    // public void visit(BreakStmt breakStmt, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Break Statement");
    //     super.visit(breakStmt, g);
    // }

    // @Override
    // public void visit(ContinueStmt continueStmt, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Continue Statement");
    //     super.visit(continueStmt, g);
    // }

    // @Override
    // public void visit(LabeledStmt labeledStmt, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Labeled Statement");
    //     super.visit(labeledStmt, g);
    // }

    // @Override
    // public void visit(ReturnStmt returnStmt, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Return Statement");
    //     super.visit(returnStmt, g);
    // }

    // @Override
    // public void visit(SwitchEntry switchEntry, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Switch Entry");
    //     super.visit(switchEntry, g);
    // }

    // @Override
    // public void visit(SwitchStmt switchStmt, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Switch Statement");
    //     super.visit(switchStmt, g);
    // }

    // TODO: Handle TryStmt, CatchClause, ThrowStmt, and YieldStmt as well

}
