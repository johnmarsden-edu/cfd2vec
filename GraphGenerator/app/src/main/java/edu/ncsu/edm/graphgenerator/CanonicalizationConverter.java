package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public class CanonicalizationConverter extends VoidVisitorAdapter<Void> {

    @Override
    public void visit(VariableDeclarator variableDeclarator, Void ignored) {
        variableDeclarator.setName("VAR");
    }

    @Override
    public void visit(NameExpr nameExpr, Void ignored) {
        nameExpr.setName("VAR");
    }

    private static void replaceNode(Node baseNode, Node replaceWith) {
        baseNode.getParentNode().ifPresent(
                parent -> parent.replace(baseNode, replaceWith)
        );
    }

    private static final Node boolLit = StaticJavaParser.parseExpression("BOOL_LIT");

    @Override
    public void visit(BooleanLiteralExpr booleanLiteralExpr, Void ignored) {
        replaceNode(booleanLiteralExpr, boolLit.clone());
    }

    private static final Node charLit = StaticJavaParser.parseExpression("CHAR_LIT");

    @Override
    public void visit(CharLiteralExpr charLiteralExpr, Void ignored) {
        replaceNode(charLiteralExpr, charLit.clone());
    }

    private static final Node doubleLit = StaticJavaParser.parseExpression("DOUBLE_LIT");

    @Override
    public void visit(DoubleLiteralExpr doubleLiteralExpr, Void ignored) {
        replaceNode(doubleLiteralExpr, doubleLit.clone());
    }

    private static final Node intLit = StaticJavaParser.parseExpression("INT_LIT");

    @Override
    public void visit(IntegerLiteralExpr integerLiteralExpr, Void ignored) {
        replaceNode(integerLiteralExpr, intLit.clone());
    }

    private static final Node longLit = StaticJavaParser.parseExpression("LONG_LIT");

    @Override
    public void visit(LongLiteralExpr longLiteralExpr, Void ignored) {
        replaceNode(longLiteralExpr, longLit.clone());
    }

    private static final Node stringLit = StaticJavaParser.parseExpression("STR_LIT");

    @Override
    public void visit(StringLiteralExpr stringLiteralExpr, Void ignored) {
        replaceNode(stringLiteralExpr, stringLit.clone());
    }

    private static final Node textBlockLit = StaticJavaParser.parseExpression("TEXT_BLOCK_LIT");

    @Override
    public void visit(TextBlockLiteralExpr textBlockLiteralExpr, Void ignored) {
        replaceNode(textBlockLiteralExpr, textBlockLit.clone());
    }
}
