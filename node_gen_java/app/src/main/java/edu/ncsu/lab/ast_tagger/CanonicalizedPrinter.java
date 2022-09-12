package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.visitor.VoidVisitor;
import com.github.javaparser.printer.DefaultPrettyPrinter;
import com.github.javaparser.printer.configuration.DefaultConfigurationOption;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.printer.configuration.PrinterConfiguration;

import java.util.function.Function;

public class CanonicalizedPrinter extends DefaultPrettyPrinter {
    private static PrinterConfiguration createDefaultConfiguration() {
        var config = new DefaultPrinterConfiguration();
        config.removeOption(new DefaultConfigurationOption(
                DefaultPrinterConfiguration.ConfigOption.PRINT_COMMENTS));
        return config;
    }

    private static Function<PrinterConfiguration, VoidVisitor<Void>> createDefaultVisitor() {
        return CanonicalizedPrinterVisitor::new;
    }

    public CanonicalizedPrinter() {
        super(createDefaultVisitor(), createDefaultConfiguration());
    }

    public String canonicalize(Parameter param) {
        //        System.out.println("Canonicalizing parameter: " + param);
        return param.getTypeAsString() + " " + this.canonicalize(param.getNameAsExpression());
    }

    public String canonicalize(Expression expression) {
        return this.print(expression);
    }

    public String canonicalize(Statement statement) {
        String output = this.print(statement);
        if (output.endsWith(";")) {
            return output.substring(0, output.length() - 1);
        }
        return output;
    }
}
