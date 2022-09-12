package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.ast.expr.*;

public class LiteralCanonicalizerVisitor extends CanonicalizerVisitor {
    @Override
    public void visit(BooleanLiteralExpr booleanLiteralExpr, Void ignored) {
        super.tag(booleanLiteralExpr);
        super.visit(booleanLiteralExpr, ignored);
    }

    @Override
    public void visit(CharLiteralExpr charLiteralExpr, Void ignored) {
        super.tag(charLiteralExpr);
        super.visit(charLiteralExpr, ignored);
    }

    @Override
    public void visit(DoubleLiteralExpr doubleLiteralExpr, Void ignored) {
        super.tag(doubleLiteralExpr);
        super.visit(doubleLiteralExpr, ignored);
    }

    @Override
    public void visit(IntegerLiteralExpr integerLiteralExpr, Void ignored) {
        super.tag(integerLiteralExpr);
        super.visit(integerLiteralExpr, ignored);
    }

    @Override
    public void visit(LongLiteralExpr longLiteralExpr, Void ignored) {
        super.tag(longLiteralExpr);
        super.visit(longLiteralExpr, ignored);
    }

    @Override
    public void visit(StringLiteralExpr stringLiteralExpr, Void ignored) {
        super.tag(stringLiteralExpr);
        super.visit(stringLiteralExpr, ignored);
    }

    @Override
    public void visit(TextBlockLiteralExpr textBlockLiteralExpr, Void ignored) {
        super.tag(textBlockLiteralExpr);
        super.visit(textBlockLiteralExpr, ignored);
    }
}
