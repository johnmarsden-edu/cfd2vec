package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.visitor.CloneVisitor;
import org.javatuples.Pair;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

public class CondExprToIfConverter {
    static AtomicInteger numberOfMethodsWithCondExpr = new AtomicInteger(0);
    public void rewriteAllCondExprsToIf(Node node) {
        Optional<ConditionalExpr> condExpr = node.findFirst(ConditionalExpr.class);
        if (condExpr.isPresent()) numberOfMethodsWithCondExpr.incrementAndGet();
        while (condExpr.isPresent()) {
            this.rewriteCondExprToIf(condExpr.get());
            condExpr = node.findFirst(ConditionalExpr.class);
        }
    }
    public void rewriteCondExprToIf(ConditionalExpr conditionalExpr) {
        Optional<Statement> parentStatement = conditionalExpr.findAncestor(Statement.class);
        if (parentStatement.isPresent()) {
            Statement parent = parentStatement.get();
            // System.out.println(parent);

            if (parent instanceof ExpressionStmt e) {
                // System.out.println("Expression Stmt");
                Expression expr = e.getExpression();
                // If expr is a variable declaration split parent into a variable declaration
                // expression statement and an assignment expression statement.
                rewriteExpressionStatement(conditionalExpr, parent, e, expr);
                // System.out.println("Rewrote Expression Statement");
            } else {
                rewriteCondAsIfElse(conditionalExpr, parent);
            }
        }
    }

    private void rewriteExpressionStatement(ConditionalExpr conditionalExpr, Statement parent, ExpressionStmt e, Expression expr) {
        Pair<Statement, Statement> parentAndStatement = Pair.with(parent, e);
        if (expr instanceof VariableDeclarationExpr varDecExpr) {
            // System.out.println("Extracted Declaration");
            parentAndStatement = extractConditionalDeclaration(conditionalExpr, parent, varDecExpr);
            // System.out.println(parentAndStatement.getValue0());
            // System.out.println(parentAndStatement.getValue1());
        }

        // If expr is a condExpr, convert parent into a block statement containing an if
        // else
        if (expr instanceof ConditionalExpr condExpr && e.getParentNode().isPresent() &&
                (e.getParentNode().get() instanceof SwitchEntry || e.getParentNode().get() instanceof LambdaExpr)) {
            // System.out.println("Rewrote a switch or Lambda");
            rewriteSwitchOrLambda(e, condExpr);
        } else {
            // System.out.println("Rewrite Conditional as If Statement");
            rewriteCondAsIfElse(conditionalExpr, parentAndStatement.getValue1());
            // System.out.println(conditionalExpr);
            // System.out.println(parentAndStatement.getValue1());
        }
//        throw new RuntimeException("Stop execution fast");
    }

    private void rewriteCondAsIfElse(ConditionalExpr conditionalExpr, Statement originalStatement) {
        // If it's none of these we create an if-else above the parent statement, duplicate
        // the parent statement and replace the conditional expression in one with the first
        // and the conditional expression in the other with the second.

        // don't forget to delete the original parent statement or move it
        // into one of the branches.


        CloneVisitor cloner = new CloneVisitor();
        Statement trueClone = (Statement) originalStatement.accept(cloner, null);
        Statement falseClone = (Statement) originalStatement.accept(cloner, null);
        ConditionalExpr tcCondExpr = trueClone.findFirst(ConditionalExpr.class, (c) -> c.toString().equals(conditionalExpr.toString())).orElseThrow();
        ConditionalExpr fcCondExpr = falseClone.findFirst(ConditionalExpr.class, (c) -> c.toString().equals(conditionalExpr.toString())).orElseThrow();
        tcCondExpr.replace(tcCondExpr.getThenExpr());
        // System.out.println(trueClone);
        fcCondExpr.replace(fcCondExpr.getElseExpr());
        // System.out.println(falseClone);

        IfStmt ifStmt = new IfStmt(conditionalExpr.getCondition(), trueClone, falseClone);

        Node parent = originalStatement.getParentNode().orElseThrow();
        // System.out.println(parent);
        originalStatement.replace(ifStmt);
        // System.out.println(parent);
    }

    private Pair<Statement, Statement> extractConditionalDeclaration(ConditionalExpr conditionalExpr, Statement parent, VariableDeclarationExpr varDecExpr) {
        Statement newParent = null;
        Statement newStatement = null;
        if (parent.getParentNode().isPresent() && parent.getParentNode().get() instanceof BlockStmt blockStmt) {
            int parentIndex = blockStmt.getStatements().indexOf(parent);

            VariableDeclarator condDeclarator = conditionalExpr.findAncestor(VariableDeclarator.class).orElseThrow();
            NodeList<VariableDeclarator> irrelevantVariables = new NodeList<>();
            for (VariableDeclarator var : varDecExpr.getVariables()) {
                if (var != condDeclarator) {
                    irrelevantVariables.add(new VariableDeclarator(var.getType(), var.getName()));
                }
            }

            ExpressionStmt irrelevantDeclaration = new ExpressionStmt(
                    new VariableDeclarationExpr(irrelevantVariables)
            );

            ExpressionStmt declaration = new ExpressionStmt(
                    new VariableDeclarationExpr(
                            new VariableDeclarator(
                                    condDeclarator.getType(),
                                    condDeclarator.getName()
                            )
                    )
            );

            ExpressionStmt assignment = new ExpressionStmt(
                    new AssignExpr(
                            condDeclarator.getNameAsExpression(),
                            condDeclarator.getInitializer().orElseThrow(),
                            AssignExpr.Operator.ASSIGN
                    )
            );

            blockStmt.addStatement(parentIndex, assignment);
            blockStmt.addStatement(parentIndex, declaration);
            if (irrelevantDeclaration.getExpression().asVariableDeclarationExpr().getVariables().isNonEmpty()) {
                blockStmt.addStatement(parentIndex, irrelevantDeclaration);
                newParent = irrelevantDeclaration;
            } else {
                newParent = declaration;
            }
            blockStmt.remove(parent);
            newStatement = assignment;
        }
        return Pair.with(newParent, newStatement);
    }

    private void rewriteSwitchOrLambda(ExpressionStmt e, ConditionalExpr condExpr) {
        // if parent belongs to a switch entry, make each branch use yield statement
        // if parent belongs to a lambda expression, make each branch a return statement

        IfStmt ifStmt = new IfStmt().setCondition(condExpr.getCondition());
        BlockStmt blockStmt = new BlockStmt(new NodeList<>(ifStmt));

        if (e.getParentNode().isPresent() && e.getParentNode().get() instanceof SwitchEntry switchEntry) {
            ifStmt.setThenStmt(new YieldStmt(condExpr.getThenExpr()))
                  .setElseStmt(new YieldStmt(condExpr.getElseExpr()));
            switchEntry.setStatements(new NodeList<>(blockStmt));
        } else if (e.getParentNode().isPresent() && e.getParentNode().get() instanceof LambdaExpr lambdaExpr) {
            ifStmt.setThenStmt(new ReturnStmt(condExpr.getThenExpr()))
                  .setElseStmt(new ReturnStmt(condExpr.getElseExpr()));
            lambdaExpr.setBody(blockStmt);
        }
    }
}
