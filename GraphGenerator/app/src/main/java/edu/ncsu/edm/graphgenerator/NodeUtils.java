package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.ast.stmt.Statement;

public class NodeUtils {
    public static Statement getParentStatement(Statement stmt) {
        return stmt.findAncestor(Statement.class, s -> s.isDoStmt() || s.isForEachStmt() ||
               s.isForStmt() || s.isIfStmt() ||
               s.isWhileStmt()
        ).get();
    }
}
