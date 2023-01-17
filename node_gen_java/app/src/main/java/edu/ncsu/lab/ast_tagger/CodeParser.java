package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;

import java.util.List;
import java.util.Optional;

public class CodeParser {

    public static JavaParser createNewJavaParser() {
        TypeSolver reflectionSolver = new ReflectionTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(reflectionSolver);
        ParserConfiguration configuration = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17_PREVIEW)
                .setSymbolResolver(symbolSolver)
                .setDoNotAssignCommentsPrecedingEmptyLines(true)
                .setAttributeComments(false);
        return new JavaParser(configuration);
    }

    public static Optional<CompilationUnit> parseCode(JavaParser parser, List<String> imports,
                                                      String code, boolean hasClass) {
        StringBuilder source = new StringBuilder();
        for (String importTarget : imports) {
            source.append("import ").append(importTarget).append(";\n");
        }
        if (!hasClass) {
            source.append("public class MethodWrapper {\n");
        }
        source.append(code);
        if (!hasClass) {
            source.append("\n}");
        }

        var result = parser.parse(source.toString());
        if (result.isSuccessful()) {
            return result.getResult();
        }

        return Optional.empty();
    }
}
