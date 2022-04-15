package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.ast.expr.FieldAccessExpr;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

import java.util.Set;

public class FindStaticNamesVisitor extends VoidVisitorAdapter<Set<String>> {
    @Override
    public void visit(MethodCallExpr methodCallExpr, Set<String> staticNames) {
        try {
            if (methodCallExpr.hasScope() && methodCallExpr.resolve().isStatic()) {
                staticNames.add(methodCallExpr.getScope().get().toString());
            }
        } catch (Exception ignore) {}
        super.visit(methodCallExpr, staticNames);
    }

    @Override
    public void visit(FieldAccessExpr fieldAccessExpr, Set<String> staticNames) {
        try {
            if (fieldAccessExpr.resolve().asField().isStatic()) {
                staticNames.add(fieldAccessExpr.getScope().toString());
            }
        } catch (Exception ignore) {}
        super.visit(fieldAccessExpr, staticNames);
    }
}
