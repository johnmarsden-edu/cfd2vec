package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LiteralStringValueExpr;
import com.github.javaparser.ast.expr.StringLiteralExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.parametrization.ResolvedTypeParametersMap;
import org.jgrapht.Graph;

import java.util.*;

public class AstToGraphConverter extends VoidVisitorAdapter<Graph<FlowNode, FlowEdge>> {

    private static class Edge {
        private final FlowNode source;
        private final FlowNode target;
        private final FlowEdge edge;

        public Edge(Node source, Graph<FlowNode, FlowEdge> g) {
            FlowNode tempSource = new FlowNode(source);
            Set<FlowEdge> edgeSet = g.outgoingEdgesOf(tempSource);
            if (edgeSet.size() != 1) {
                throw new IllegalStateException("Somehow we ended up visiting the node " + source + " that had " +
                        edgeSet.size() + " outgoing edges instead of 1. Here's the graph:\n" + g);
            }
            this.edge = edgeSet.stream().findFirst().get();

            // Must be done this way in order to get access to labels
            this.source = g.getEdgeSource(this.edge);
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

    private static void addDecisionNode(FlowNode decisionNode, FlowNode trueTarget, FlowNode falseTarget, Graph<FlowNode, FlowEdge> g) {
        g.addVertex(decisionNode);
        g.addEdge(decisionNode, trueTarget, new FlowEdge(true));
        g.addEdge(decisionNode, falseTarget, new FlowEdge(false));
    }

    private static FlowNode createAndAddDecisionNode(Expression conditionalExpr, FlowNode trueTarget, FlowNode falseTarget, Graph<FlowNode, FlowEdge> g) {
        FlowNode decisionNode = new FlowNode(conditionalExpr);
        addDecisionNode(decisionNode, trueTarget, falseTarget, g);
        return decisionNode;
    }

    public static void rerouteIncomingEdges(FlowNode source, FlowNode target, Graph<FlowNode, FlowEdge> g) {
        Set<FlowEdge> incomingEdges = g.incomingEdgesOf(source);
        for (FlowEdge iEdge : incomingEdges) {
            FlowNode edgeSource = g.getEdgeSource(iEdge);
            FlowEdge edge = iEdge.getFlowCondition().isPresent() ? new FlowEdge(iEdge.getFlowCondition().get()) : new FlowEdge();
            g.addEdge(edgeSource, target, edge);
        }
    }

    public static void rerouteOutgoingEdges(FlowNode source, FlowNode target, Graph<FlowNode, FlowEdge> g) {
        Set<FlowEdge> incomingEdges = g.outgoingEdgesOf(source);
        for (FlowEdge iEdge : incomingEdges) {
            FlowNode edgeTarget = g.getEdgeTarget(iEdge);
            FlowEdge edge = iEdge.getFlowCondition().isPresent() ? new FlowEdge(iEdge.getFlowCondition().get()) : new FlowEdge();
            g.addEdge(target, edgeTarget, edge);
        }
    }

    @Override
    public void visit(MethodDeclaration methodDeclaration, Graph<FlowNode, FlowEdge> g) {
        FlowNode callNode = new FlowNode("before");
        FlowNode finishNode = new FlowNode("after");
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
        rerouteIncomingEdges(mdNode, g.getEdgeTarget(g.outgoingEdgesOf(mdNode).stream().findFirst().orElseThrow()), g);
        g.removeVertex(mdNode);
    }

    private static int numIndexes = 0;
    private static int numIterators = 0;

    @Override
    public void visit(ForEachStmt forEachStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(forEachStmt, g);
        FlowNode body = new FlowNode(forEachStmt.getBody());
        g.addVertex(body);
        // We're dealing with an array and should represent it with an indexed for loop
        ResolvedType iterableType = forEachStmt.getIterable().calculateResolvedType();
        Optional<ResolvedReferenceTypeDeclaration> itType = Optional.empty();
        if (iterableType.isReferenceType()) {
            itType = forEachStmt.getIterable().calculateResolvedType().asReferenceType().getTypeDeclaration();
        }
        boolean isString = itType.isPresent() && itType.get().getClassName().equals("String");

        FlowNode startNode;
        FlowNode decisionNode;
        if (iterableType.isArray() || isString) {
            String varType = forEachStmt.getVariable().getVariables().getFirst().orElseThrow().getTypeAsString();
            String varName = forEachStmt.getVariable().getVariables().getFirst().orElseThrow().getNameAsString();
            FlowNode varDecl = new FlowNode(StaticJavaParser.parseStatement(varType + " " + varName + ";"));
            g.addVertex(varDecl);

            FlowNode indexInit = new FlowNode(StaticJavaParser.parseStatement("int index" + numIndexes + " = 0;"));
            g.addVertex(indexInit);

            String getString = isString ? ".get(index" + numIndexes + ");" : "[index" + numIndexes + "];";
            FlowNode varSet = new FlowNode(StaticJavaParser.parseStatement(varName + " = " +
                    forEachStmt.getIterable() + getString));
            g.addVertex(varSet);

            FlowNode updateIndex = new FlowNode(StaticJavaParser.parseStatement("index" + numIndexes + "++;"));
            g.addVertex(updateIndex);

            String lengthString = isString ? ".length()" : ".length";
            decisionNode = createAndAddDecisionNode(
                    StaticJavaParser.parseExpression(
                            "index" + numIndexes + " < " + forEachStmt.getIterable() + lengthString
                    ),
                    varSet,
                    edge.getTarget(),
                    g
            );
            g.addEdge(varDecl, indexInit);
            g.addEdge(indexInit, decisionNode);
            g.addEdge(varSet, body);
            g.addEdge(body, updateIndex);
            g.addEdge(updateIndex, decisionNode);
            startNode = varDecl;
            numIndexes++;
        } else { // We're dealing with an iterable and should represent it with an iterator for loop
            ResolvedTypeParametersMap itTypeParams = iterableType.asReferenceType().typeParametersMap();

            if (itTypeParams.getTypes().size() != 1) {
                throw new IllegalStateException("An iterable with more than one type was given: " + itTypeParams);
            }

            FlowNode iteratorInit = new FlowNode(StaticJavaParser.parseStatement("Iterator<" +
                    itTypeParams.getTypes().get(0).asReferenceType().getQualifiedName() + "> iterator" +
                    numIterators + " = " + forEachStmt.getIterable() + ".iterator();"));
            g.addVertex(iteratorInit);

            String varType = forEachStmt.getVariable().getVariables().getFirst().orElseThrow().getTypeAsString();
            String varName = forEachStmt.getVariable().getVariables().getFirst().orElseThrow().getNameAsString();
            FlowNode varDecl = new FlowNode(StaticJavaParser.parseStatement(varType + " " + varName + ";"));
            g.addVertex(varDecl);

            FlowNode varSet = new FlowNode(StaticJavaParser.parseStatement(varName + " = iterator" + numIterators +
                    ".next();"));
            g.addVertex(varSet);

            decisionNode = createAndAddDecisionNode(
                    StaticJavaParser.parseExpression(
                            "iterator" + numIterators + ".hasNext()"
                    ),
                    varSet,
                    edge.getTarget(),
                    g
            );
            g.addEdge(varDecl, iteratorInit);
            g.addEdge(iteratorInit, decisionNode);
            g.addEdge(varSet, body);
            g.addEdge(body, decisionNode);
            startNode = varDecl;
            numIterators++;
        }

        g.addEdge(edge.getSource(), startNode);
        g.removeEdge(edge.getEdge());
        g.addEdge(edge.getSource(), edge.getTarget(), new FlowEdge(false));
        g.addEdge(edge.getSource(), decisionNode, new FlowEdge(true));

        super.visit(forEachStmt, g);
        rerouteIncomingEdges(edge.getSource(), startNode, g);
        g.removeVertex(edge.getSource());
    }

    @Override
    public void visit(ForStmt forStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(forStmt, g);
        FlowNode parent = edge.getSource();

        FlowNode target = edge.getTarget();

        // Create links for the initialization steps
        FlowNode startNode = null;
        FlowNode curParent = null;
        if (forStmt.getInitialization().getFirst().isPresent()) {
            curParent = new FlowNode(forStmt.getInitialization().getFirst().get());
            g.addVertex(curParent);
            startNode = curParent;
            for (Expression expr : forStmt.getInitialization().stream().skip(1).toList()) {
                FlowNode exprNode = new FlowNode(expr);
                g.addVertex(exprNode);
                g.addEdge(curParent, exprNode, new FlowEdge());
                curParent = exprNode;
            }
        }

        // Create links from initialization step to conditional
        FlowNode decisionNode = null;
        if (forStmt.getCompare().isPresent()) {
            decisionNode = new FlowNode(forStmt.getCompare().get());
            g.addVertex(decisionNode);
            if (startNode == null) {
                startNode = decisionNode;
            } else {
                g.addEdge(curParent, decisionNode);
            }
        }

        // Create links from for statement or initialization step to body block
        FlowNode body = new FlowNode(forStmt.getBody());
        g.addVertex(body);
        if (startNode == null) {
            startNode = body;
            decisionNode = body;
        } else if (decisionNode == null) {
            decisionNode = body;
        } else {
            addDecisionNode(decisionNode, body, edge.getTarget(), g);
        }

        // Create links from body to update step
        FlowNode updateNode = null;
        if (forStmt.getUpdate().getFirst().isPresent()) {
            updateNode = new FlowNode(forStmt.getUpdate().getFirst().get());
            g.addVertex(updateNode);
            g.addEdge(body, updateNode);
            for (Expression expr : forStmt.getUpdate().stream().skip(1).toList()) {
                FlowNode temp = new FlowNode(expr);
                g.addVertex(temp);
                g.addEdge(updateNode, temp);
                updateNode = temp;
            }
        }

        // Create links from update step to conditional step
        g.addEdge(Objects.requireNonNullElse(updateNode, body), parent);
        g.addEdge(parent, startNode);
        g.addEdge(parent, Objects.requireNonNullElse(updateNode, decisionNode), new FlowEdge(true));
        g.removeEdge(edge.getEdge());
        g.addEdge(parent, target, new FlowEdge(false));

        super.visit(forStmt, g);
        Optional<FlowNode> nextStartNode = g.outgoingEdgesOf(parent).stream()
                .filter(e -> e.getFlowCondition().isEmpty())
                .map(g::getEdgeTarget)
                .findFirst();
        if (nextStartNode.isPresent()) {
            rerouteIncomingEdges(parent, nextStartNode.get(), g);
            g.removeVertex(parent);
        }
    }

    @Override
    public void visit(IfStmt ifStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(ifStmt, g);
        FlowNode falseTarget;

        if (ifStmt.getElseStmt().isPresent()) {
            falseTarget = new FlowNode(ifStmt.getElseStmt().get());
            g.addVertex(falseTarget);
            g.addEdge(falseTarget, edge.getTarget());
        } else {
            falseTarget = edge.getTarget();
        }


        FlowNode trueTarget = new FlowNode(ifStmt.getThenStmt());
        g.addVertex(trueTarget);

        FlowNode decisionNode = createAndAddDecisionNode(ifStmt.getCondition(), trueTarget, falseTarget, g);
        g.addEdge(trueTarget, edge.getTarget());
        g.addEdge(edge.getSource(), decisionNode);
        g.removeEdge(edge.getEdge());
        g.addEdge(edge.getSource(), edge.getTarget(), new FlowEdge(false));
        super.visit(ifStmt, g);
        rerouteIncomingEdges(edge.getSource(), decisionNode, g);
        g.removeVertex(edge.getSource());
    }

    @Override
    public void visit(BlockStmt blockStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(blockStmt, g);

        if (blockStmt.isEmpty()) {
            rerouteIncomingEdges(edge.getSource(), edge.getTarget(), g);
            g.removeVertex(edge.getSource());
        } else {
            FlowNode firstStatement = new FlowNode(blockStmt.getStatement(0));
            g.addVertex(firstStatement);

            FlowNode parent = firstStatement;
            for (Statement stmt : blockStmt.getStatements().stream().skip(1).toList()) {
                FlowNode target = new FlowNode(stmt);
                g.addVertex(target);
                g.addEdge(parent, target);
                parent = target;
            }
            g.addEdge(parent, edge.getTarget());
            rerouteIncomingEdges(edge.getSource(), firstStatement, g);
            g.removeVertex(edge.getSource());
        }
        super.visit(blockStmt, g);
    }

    @Override
    public void visit(WhileStmt whileStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(whileStmt, g);
        FlowNode decisionNode = new FlowNode(whileStmt.getCondition());
        FlowNode body = new FlowNode(whileStmt.getBody());
        g.addVertex(decisionNode);
        g.addVertex(body);
        g.removeEdge(edge.getEdge());
        g.addEdge(edge.getSource(), edge.getTarget(), new FlowEdge(false));
        g.addEdge(edge.getSource(), decisionNode, new FlowEdge(true));
        addDecisionNode(decisionNode, body, edge.getTarget(), g);
        g.addEdge(body, edge.getSource());
        super.visit(whileStmt, g);
        rerouteIncomingEdges(edge.getSource(), decisionNode, g);
        g.removeVertex(edge.getSource());
    }

    @Override
    public void visit(LabeledStmt labeledStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(labeledStmt, g);
        FlowNode labeledNode = new FlowNode(labeledStmt.getStatement(), labeledStmt.getLabel().asString());
        g.addVertex(labeledNode);
        rerouteIncomingEdges(edge.getSource(), labeledNode, g);
        g.removeVertex(edge.getSource());
        g.addEdge(labeledNode, edge.getTarget());
        super.visit(labeledStmt, g);
    }

    // @Override
    public void visit(DoStmt doWhileStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(doWhileStmt, g);
        FlowNode body = new FlowNode(doWhileStmt.getBody());
        g.addVertex(body);
        FlowNode decisionNode = createAndAddDecisionNode(doWhileStmt.getCondition(), body, edge.getTarget(), g);
        g.addEdge(body, decisionNode);
        g.addEdge(edge.getSource(), body);
        g.addEdge(edge.getSource(), edge.getTarget(), new FlowEdge(false));
        g.addEdge(edge.getSource(), decisionNode, new FlowEdge(true));
        super.visit(doWhileStmt, g);
        body = g.outgoingEdgesOf(edge.getSource()).stream().filter(e -> e.getFlowCondition().isEmpty())
                .map(g::getEdgeTarget).findFirst().orElseThrow();
        rerouteIncomingEdges(edge.getSource(), body, g);
    }

    // @Override
    // public void visit(ConditionalExpr ternaryExpr, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Ternary Expression");
    //     super.visit(ternaryExpr, g);
    // }

    @Override
    public void visit(BreakStmt breakStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(breakStmt, g);
        FlowNode ancestor;
        if (breakStmt.getLabel().isPresent()) {
            ancestor = NodeUtils.getLabeledAncestor(g, edge.getSource(), breakStmt.getLabel().get().asString());
        } else {
            ancestor = NodeUtils.getNearestBreakableAncestor(g, edge.getSource());
        }
        ancestor = g.getEdgeTarget(g.outgoingEdgesOf(ancestor).stream()
                    .filter(e -> e.getFlowCondition().isPresent() && !e.getFlowCondition().get())
                    .findFirst().orElseThrow());

        Collection<FlowEdge> edges = g.outgoingEdgesOf(edge.getSource()).stream().toList();
        g.removeAllEdges(edges);
        rerouteIncomingEdges(edge.getSource(), ancestor, g);
        g.removeVertex(edge.getSource());
        super.visit(breakStmt, g);
    }

    @Override
    public void visit(ContinueStmt continueStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(continueStmt, g);
        FlowNode ancestor;
        if (continueStmt.getLabel().isPresent()) {
            ancestor = NodeUtils.getLabeledAncestor(g, edge.getSource(), continueStmt.getLabel().get().asString());
            if (ancestor.getNode().isPresent() && ancestor.getNode().get() instanceof IfStmt) {
                throw new IllegalStateException("Tried to continue to a labeled if statement");
            }
        } else {
            ancestor = NodeUtils.getNearestContinuableAncestor(g, edge.getSource());
        }
        ancestor = g.getEdgeTarget(g.outgoingEdgesOf(ancestor).stream()
             .filter(e -> e.getFlowCondition().isPresent() && e.getFlowCondition().get())
             .findFirst().orElseThrow());

        Collection<FlowEdge> edges = g.outgoingEdgesOf(edge.getSource()).stream().toList();
        g.removeAllEdges(edges);
        rerouteIncomingEdges(edge.getSource(), ancestor, g);
        g.removeVertex(edge.getSource());
        super.visit(continueStmt, g);
    }

    @Override
    public void visit(ReturnStmt returnStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(returnStmt, g);
        FlowNode after = NodeUtils.getNearestReturnableAncestor(g, edge.getSource());
        g.removeAllEdges(g.outgoingEdgesOf(edge.getSource()).stream().toList());
        g.addEdge(edge.getSource(), after);
        super.visit(returnStmt, g);
    }

    @Override
    public void visit(SwitchEntry switchEntry, Graph<FlowNode, FlowEdge> g) {
        FlowNode switchNode = new FlowNode(switchEntry);
        switchNode = g.getEdgeTarget(g.incomingEdgesOf(switchNode).stream().findFirst().orElseThrow());
        Expression cond = null;
        if (switchEntry.getLabels().isNonEmpty()) {
            StringBuilder sb = new StringBuilder()
                    .append(switchNode.getMetadata("varname"))
                    .append(" == ")
                    .append(switchEntry.getLabels().get(0));
            for (Expression e: switchEntry.getLabels().stream().skip(1).toList()) {
                sb.append(" || ")
                  .append(switchNode.getMetadata("varname"))
                  .append(" == ")
                  .append(e);
            }
            cond = StaticJavaParser.parseExpression(sb.toString());
            FlowNode condNode = new FlowNode(cond);
            g.addVertex(condNode);
            rerouteIncomingEdges(switchNode, condNode, g);
            rerouteOutgoingEdges(switchNode, condNode, g);
            g.removeVertex(switchNode);
        }
        Node entryNode = cond == null ? switchEntry : cond;
        final Edge edge = new Edge(entryNode, g);
        FlowNode to = null;
        if (switchEntry.getStatements().isEmpty()) {
            to = edge.getTarget();
        } else {
            FlowNode parent = null;
            for (Statement s: switchEntry.getStatements()) {
                FlowNode current = new FlowNode(s);
                g.addVertex(current);
                if (to == null) {
                    to = current;
                } else {
                    g.addEdge(parent, current);
                }
                parent = current;
            }
            g.addEdge(parent, edge.getTarget(), new FlowEdge(true));
        }

        List<FlowEdge> remapEdges = g.incomingEdgesOf(edge.getSource()).stream()
                .filter(e -> e.getFlowCondition().isPresent() && e.getFlowCondition().get()).toList();
        if (!remapEdges.isEmpty()) {
            for (FlowEdge e: remapEdges) {
                FlowNode from = g.getEdgeSource(e);
                g.removeEdge(e);
                g.addEdge(from, to, new FlowEdge(true));
            }
        }
        if (switchEntry.getLabels().isEmpty()) {
            g.addEdge(edge.getSource(), to);
        } else {
            g.addEdge(edge.getSource(), to, new FlowEdge(true));
        }
        super.visit(switchEntry, g);
        if (switchEntry.getLabels().isEmpty()) {
            FlowEdge firstEdgeToNode = g.outgoingEdgesOf(edge.getSource()).stream().filter(e -> e.getFlowCondition().isEmpty())
                    .findFirst().orElseThrow();
            rerouteIncomingEdges(edge.getSource(), g.getEdgeTarget(firstEdgeToNode), g);
            g.removeVertex(edge.getSource());
        }
    }

     @Override
    public void visit(SwitchStmt switchStmt, Graph<FlowNode, FlowEdge> g) {
        final Edge edge = new Edge(switchStmt, g);
        g.removeEdge(edge.getEdge());
        g.addEdge(edge.getSource(), edge.getTarget(), new FlowEdge(false));

        FlowNode parent = edge.getSource();
        for (SwitchEntry se : switchStmt.getEntries()) {
            FlowNode current = new FlowNode(se);
            current.addMetadata("varname", switchStmt.getSelector().toString());
            g.addVertex(current);
            g.addEdge(parent, current, new FlowEdge(false));
            parent = current;
        }
        g.addEdge(parent, edge.getTarget(), new FlowEdge(false));
        super.visit(switchStmt, g);
        FlowNode first = g.getEdgeTarget(g.outgoingEdgesOf(edge.getSource()).stream()
                .filter(e -> !g.getEdgeTarget(e).equals(edge.getTarget()))
                .findFirst().orElseThrow());
        rerouteIncomingEdges(edge.getSource(), first, g);
        g.removeVertex(edge.getSource());
    }

    // TODO: Handle TryStmt, CatchClause, ThrowStmt, and YieldStmt as well
    @Override
    public void visit(TryStmt tryStmt, Graph<FlowNode, FlowEdge> g) {
        throw new UnsupportedOperationException("Try statements are not yet supported");
    }

    @Override
    public void visit(ThrowStmt throwStmt, Graph<FlowNode, FlowEdge> g) {
        throw new UnsupportedOperationException("Throw statements are not yet supported");
    }
}
