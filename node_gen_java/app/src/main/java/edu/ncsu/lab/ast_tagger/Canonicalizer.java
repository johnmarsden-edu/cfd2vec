package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.stmt.Statement;

public abstract class Canonicalizer {

    public abstract String canonicalize(Expression expression);

    public abstract String canonicalize(Statement statement);

    public String canonicalize(Parameter param) {
        //        System.out.println("Canonicalizing parameter: " + param);
        return param.getTypeAsString() + " " + this.canonicalize(param.getNameAsExpression());
    }

    public static class None extends Canonicalizer {
        @Override
        public String canonicalize(Expression expression) {
            return expression.toString();
        }

        @Override
        public String canonicalize(Statement statement) {
            String output = statement.toString();
            if (output.endsWith(";")) {
                return output.substring(0, output.length() - 1);
            }
            return output;
        }
    }

    public static class Literal extends Canonicalizer {
        LiteralCanonicalizerVisitor converter = new LiteralCanonicalizerVisitor();


        @Override
        public String canonicalize(Expression expression) {
            expression = expression.clone();
            expression.accept(converter, null);
            return expression.toString();
        }

        @Override
        public String canonicalize(Statement statement) {
            statement = statement.clone();
            statement.accept(converter, null);
            String output = statement.toString();
            if (output.endsWith(";")) {
                return output.substring(0, output.length() - 1);
            }
            return output;
        }
    }

    public static class Variable extends Canonicalizer {
        VariableCanonicalizerVisitor variableCanonicalizer = new VariableCanonicalizerVisitor();


        @Override
        public String canonicalize(Expression expression) {
            expression = expression.clone();
            expression.accept(variableCanonicalizer, null);
            return expression.toString();
        }

        @Override
        public String canonicalize(Statement statement) {
            statement = statement.clone();
            statement.accept(variableCanonicalizer, null);
            String output = statement.toString();
            if (output.endsWith(";")) {
                return output.substring(0, output.length() - 1);
            }
            return output;
        }
    }

    public static class Full extends Canonicalizer {
        VariableCanonicalizerVisitor variableCanonicalizer = new VariableCanonicalizerVisitor();
        LiteralCanonicalizerVisitor literalCanonicalizer = new LiteralCanonicalizerVisitor();


        @Override
        public String canonicalize(Expression expression) {
            expression = expression.clone();
            //            System.out.println(expression);
            expression.accept(variableCanonicalizer, null);
            //            System.out.println(expression);
            expression.accept(literalCanonicalizer, null);
            //            System.out.println(expression);
            return expression.toString();
        }

        @Override
        public String canonicalize(Statement statement) {
            statement = statement.clone();
            statement.accept(variableCanonicalizer, null);
            statement.accept(literalCanonicalizer, null);
            String output = statement.toString();
            if (output.endsWith(";")) {
                return output.substring(0, output.length() - 1);
            }
            return output;
        }
    }
}
