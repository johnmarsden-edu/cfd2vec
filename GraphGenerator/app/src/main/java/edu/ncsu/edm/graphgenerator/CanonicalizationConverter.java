package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.UnsolvedSymbolException;

import java.util.Set;

public class CanonicalizationConverter extends VoidVisitorAdapter<Set<String>> {

    @Override
    public void visit(VariableDeclarator variableDeclarator, Set<String> ignored) {
        variableDeclarator.setName("VAR");
        super.visit(variableDeclarator, ignored);
    }

    @Override
    public void visit(NameExpr nameExpr, Set<String> staticNames) {
        if (!staticNames.contains(nameExpr.getNameAsString())) {
            nameExpr.setName("VAR");
        }
        super.visit(nameExpr, staticNames);
    }

    private static void replaceNode(Node baseNode, Node replaceWith) {
        baseNode.getParentNode().ifPresent(
                parent -> parent.replace(baseNode, replaceWith)
        );
    }

    private static final Node boolLit = StaticJavaParser.parseExpression("BOOL_LIT");

    @Override
    public void visit(BooleanLiteralExpr booleanLiteralExpr, Set<String> ignored) {
        replaceNode(booleanLiteralExpr, boolLit.clone());
        super.visit(booleanLiteralExpr, ignored);
    }

    private static final Node charLit = StaticJavaParser.parseExpression("CHAR_LIT");

    @Override
    public void visit(CharLiteralExpr charLiteralExpr, Set<String> ignored) {
        replaceNode(charLiteralExpr, charLit.clone());
        super.visit(charLiteralExpr, ignored);
    }

    private static final Node doubleLit = StaticJavaParser.parseExpression("DOUBLE_LIT");

    @Override
    public void visit(DoubleLiteralExpr doubleLiteralExpr, Set<String> ignored) {
        replaceNode(doubleLiteralExpr, doubleLit.clone());
        super.visit(doubleLiteralExpr, ignored);
    }

    private static final Node intLit = StaticJavaParser.parseExpression("INT_LIT");

    @Override
    public void visit(IntegerLiteralExpr integerLiteralExpr, Set<String> ignored) {
        replaceNode(integerLiteralExpr, intLit.clone());
        super.visit(integerLiteralExpr, ignored);
    }

    private static final Node longLit = StaticJavaParser.parseExpression("LONG_LIT");

    @Override
    public void visit(LongLiteralExpr longLiteralExpr, Set<String> ignored) {
        replaceNode(longLiteralExpr, longLit.clone());
        super.visit(longLiteralExpr, ignored);
    }

    private static final Node stringLit = StaticJavaParser.parseExpression("STR_LIT");

    @Override
    public void visit(StringLiteralExpr stringLiteralExpr, Set<String> ignored) {
        replaceNode(stringLiteralExpr, stringLit.clone());
        super.visit(stringLiteralExpr, ignored);
    }

    private static final Node textBlockLit = StaticJavaParser.parseExpression("TEXT_BLOCK_LIT");

    @Override
    public void visit(TextBlockLiteralExpr textBlockLiteralExpr, Set<String> ignored) {
        replaceNode(textBlockLiteralExpr, textBlockLit.clone());
        super.visit(textBlockLiteralExpr, ignored);
    }
}
