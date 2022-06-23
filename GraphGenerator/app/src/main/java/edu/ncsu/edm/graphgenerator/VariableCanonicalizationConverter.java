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
        super.visit(variableDeclarator, ignored);
    }

    private static boolean isPossibleInvalidNameAncestor(Object o) {
        return o instanceof Statement ||
                o instanceof FieldAccessExpr ||
                o instanceof MethodCallExpr ||
                o instanceof MethodReferenceExpr;

    }

    @Override
    public void visit(SimpleName simpleName, Void ignored) {
        Optional<Object> possibleAncestor = simpleName.findAncestor(VariableCanonicalizationConverter::isPossibleInvalidNameAncestor);
        if (possibleAncestor.isPresent() && possibleAncestor.get() instanceof Statement) {
            simpleName.setIdentifier("VAR");
        }
        super.visit(simpleName, ignored);
    }
}
