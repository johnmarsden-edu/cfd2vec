package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Optional;
import java.util.Set;

public class VariableCanonicalizationConverter extends VoidVisitorAdapter<Void> {

    @Override
    public void visit(VariableDeclarator variableDeclarator, Void ignored) {
        variableDeclarator.setName("VAR");
        if (variableDeclarator.getInitializer().isPresent()) {
            variableDeclarator.getInitializer().get().accept(this, ignored);
        }
    }

    @Override
    public void visit(SimpleName simpleName, Void ignored) {
        Optional<Object> possibleAncestor = simpleName.findAncestor(t -> true, new Class[] {
                Statement.class, FieldAccessExpr.class,
                MethodCallExpr.class, MethodReferenceExpr.class
        });
        if (possibleAncestor.isPresent() && possibleAncestor.get() instanceof Statement) {
            simpleName.setIdentifier("VAR");
        }
        super.visit(simpleName, ignored);
    }
}
