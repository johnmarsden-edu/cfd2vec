package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class LiteralCanonicalizerVisitor extends VoidVisitorAdapter<Void> {
    private static final Node boolLit = StaticJavaParser.parseExpression("BOOL_LIT");

    @Override
    public void visit(BooleanLiteralExpr booleanLiteralExpr, Void ignored) {
        booleanLiteralExpr.replace(boolLit.clone());
        super.visit(booleanLiteralExpr, ignored);
    }

    private static final Node charLit = StaticJavaParser.parseExpression("CHAR_LIT");

    @Override
    public void visit(CharLiteralExpr charLiteralExpr, Void ignored) {
        charLiteralExpr.replace(charLit.clone());
        super.visit(charLiteralExpr, ignored);
    }

    private static final Node doubleLit = StaticJavaParser.parseExpression("DOUBLE_LIT");

    @Override
    public void visit(DoubleLiteralExpr doubleLiteralExpr, Void ignored) {
        doubleLiteralExpr.replace(doubleLit.clone());
        super.visit(doubleLiteralExpr, ignored);
    }

    private static final Node intLit = StaticJavaParser.parseExpression("INT_LIT");

    @Override
    public void visit(IntegerLiteralExpr integerLiteralExpr, Void ignored) {
        integerLiteralExpr.replace(intLit.clone());
        super.visit(integerLiteralExpr, ignored);
    }

    private static final Node longLit = StaticJavaParser.parseExpression("LONG_LIT");

    @Override
    public void visit(LongLiteralExpr longLiteralExpr, Void ignored) {
        longLiteralExpr.replace(longLit.clone());
        super.visit(longLiteralExpr, ignored);
    }

    private static final Node stringLit = StaticJavaParser.parseExpression("STR_LIT");

    @Override
    public void visit(StringLiteralExpr stringLiteralExpr, Void ignored) {
        stringLiteralExpr.replace(stringLit.clone());
        super.visit(stringLiteralExpr, ignored);
    }

    private static final Node textBlockLit = StaticJavaParser.parseExpression("TEXT_BLOCK_LIT");

    @Override
    public void visit(TextBlockLiteralExpr textBlockLiteralExpr, Void ignored) {
        textBlockLiteralExpr.replace(textBlockLit.clone());
        super.visit(textBlockLiteralExpr, ignored);
    }
}
