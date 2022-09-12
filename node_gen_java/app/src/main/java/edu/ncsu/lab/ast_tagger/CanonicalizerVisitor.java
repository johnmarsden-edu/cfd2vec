package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.ast.DataKey;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;

public abstract class CanonicalizerVisitor extends VoidVisitorAdapter<Void> {
    public static final DataKey<Void> CANONICALIZE = new DataKey<>() {
    };

    protected void tag(Node n) {
        n.setData(CANONICALIZE, null);
    }
}
