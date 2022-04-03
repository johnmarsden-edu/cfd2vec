package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.parametrization.ResolvedTypeParametersMap;
import org.jgrapht.Graph;

import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public class AstToGraphConverter extends VoidVisitorAdapter<Graph<FlowNode, FlowEdge>> {

    private static class Edge {
        private final FlowNode source;
        private final FlowNode target;
        private final FlowEdge edge;

        public Edge(Statement source, Graph<FlowNode, FlowEdge> g) {
            FlowNode tempSource = new FlowNode(source);
            Set<FlowEdge> edgeSet = g.outgoingEdgesOf(tempSource);
            if (edgeSet.size() != 1) {
                throw new IllegalStateException("Somehow we ended up visiting a node that had " + edgeSet.size() + " outgoing edges instead of 1");
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

        g.addEdge(edge.getSource(), decisionNode, new FlowEdge(true));

        // Create links from update step to conditional step
        g.addEdge(Objects.requireNonNullElse(updateNode, body), decisionNode);
        g.addEdge(parent, startNode);
        g.addEdge(parent, decisionNode, new FlowEdge(true));
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

        rerouteIncomingEdges(edge.getSource(), decisionNode, g);
        g.removeVertex(edge.getSource());
        super.visit(ifStmt, g);
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
        addDecisionNode(decisionNode, body, edge.getTarget(), g);
        rerouteIncomingEdges(edge.getSource(), decisionNode, g);
        g.addEdge(body, decisionNode);
        g.removeVertex(edge.getSource());
        super.visit(whileStmt, g);
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
    // public void visit(DoStmt doWhileStmt, Graph<FlowNode, FlowEdge> g) {
    //     System.out.println("Do-While Statement");
    //     super.visit(doWhileStmt, g);
    // }

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
         } else {
             ancestor = NodeUtils.getNearestBreakableAncestor(g, edge.getSource());
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
    // public void visit(SwitchStmt switchStmt, Graph<FlowNode, FlowEdge> g) {-
    //     System.out.println("Switch Statement");
    //     super.visit(switchStmt, g);
    // }

    // TODO: Handle TryStmt, CatchClause, ThrowStmt, and YieldStmt as well

}
