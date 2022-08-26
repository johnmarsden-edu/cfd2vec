package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.SimpleName;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Optional;

@SuppressWarnings("unchecked")
public class VariableCanonicalizerVisitor extends VoidVisitorAdapter<Void> {

    @Override
    public void visit(VariableDeclarator variableDeclarator, Void ignored) {
        variableDeclarator.setName("VAR");
        if (variableDeclarator.getInitializer().isPresent()) {
            variableDeclarator.getInitializer().get().accept(this, ignored);
        }
    }

    @Override
    public void visit(SimpleName simpleName, Void ignored) {
        Optional<Object> possibleAncestor = simpleName.findAncestor(
                t -> true,
                new Class[]{Statement.class, FieldAccessExpr.class, MethodCallExpr.class,
                        MethodReferenceExpr.class}
        );
        if (possibleAncestor.isPresent() && possibleAncestor.get() instanceof Statement) {
            simpleName.setIdentifier("VAR");
        }
        super.visit(simpleName, ignored);
    }
}
