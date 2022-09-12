package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.ast.expr.*;
import com.github.javaparser.printer.DefaultPrettyPrinterVisitor;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

public class CanonicalizedPrinterVisitor extends DefaultPrettyPrinterVisitor {
    public CanonicalizedPrinterVisitor() {
        this(new DefaultPrinterConfiguration());
    }

    public CanonicalizedPrinterVisitor(PrinterConfiguration configuration) {
        super(configuration);
    }

    public void visit(final BooleanLiteralExpr n, final Void arg) {
        if (n.containsData(CanonicalizerVisitor.CANONICALIZE)) {
            printer.print("BOOL_LIT");
        } else {
            super.visit(n, arg);
        }
    }

    public void visit(final CharLiteralExpr n, final Void arg) {
        if (n.containsData(CanonicalizerVisitor.CANONICALIZE)) {
            printer.print("CHAR_LIT");
        } else {
            super.visit(n, arg);
        }
    }

    public void visit(final DoubleLiteralExpr n, final Void arg) {
        if (n.containsData(CanonicalizerVisitor.CANONICALIZE)) {
            printer.print("DOUBLE_LIT");
        } else {
            super.visit(n, arg);
        }
    }

    public void visit(final IntegerLiteralExpr n, final Void arg) {
        if (n.containsData(CanonicalizerVisitor.CANONICALIZE)) {
            printer.print("INT_LIT");
        } else {
            super.visit(n, arg);
        }
    }

    public void visit(final LongLiteralExpr n, final Void arg) {
        if (n.containsData(CanonicalizerVisitor.CANONICALIZE)) {
            printer.print("LONG_LIT");
        } else {
            super.visit(n, arg);
        }
    }

    public void visit(final StringLiteralExpr n, final Void arg) {
        if (n.containsData(CanonicalizerVisitor.CANONICALIZE)) {
            printer.print("STRING_LIT");
        } else {
            super.visit(n, arg);
        }
    }

    public void visit(final TextBlockLiteralExpr n, final Void arg) {
        if (n.containsData(CanonicalizerVisitor.CANONICALIZE)) {
            printer.print("TEXT_BLOCK_LIT");
        } else {
            super.visit(n, arg);
        }
    }

    public void visit(final SimpleName n, final Void arg) {
        if (n.containsData(CanonicalizerVisitor.CANONICALIZE)) {
            printer.print("VAR");
        } else {
            super.visit(n, arg);
        }
    }
}
