package edu.ncsu.lab.ast_tagger.cli;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.visitor.CloneVisitor;
import edu.ncsu.lab.ast_tagger.*;
import picocli.CommandLine;

import java.util.List;

import static edu.ncsu.lab.ast_tagger.CfgGenServer.getConnection;
import static edu.ncsu.lab.ast_tagger.CodeParser.createNewJavaParser;
import static edu.ncsu.lab.ast_tagger.cli.Tagger.getCanonicalizers;

@CommandLine.Command(
        name = "test", description = "Test a specific feature or --all",
        mixinStandardHelpOptions = true
)
public class TaggerTest implements Runnable {

    public TaggerTest() {

    }

    @CommandLine.ArgGroup()
    TestArgs testArgs;

    static class TestArgs {
        @CommandLine.Option(names = "--all")
        public boolean runAllTests;

        @CommandLine.Parameters(arity = "1.*")
        public List<String> features;
    }

    @Override
    public void run() {
        ServerConnection connection = getConnection();
        JavaParser parser = createNewJavaParser();
        if (testArgs.runAllTests) {
            ManualTests.TEST_METHODS
                    .keySet()
                    .forEach(key -> generateTestGraphs(parser, connection, key));
        } else {
            testArgs.features.forEach(feature -> generateTestGraphs(parser, connection, feature));
        }
    }


    public static void generateTestGraphs(JavaParser parser, ServerConnection connection,
                                          String feature) {
        ProgramFileCommit first = new ProgramFileCommit(ManualTests.TEST_METHODS.get(feature));
        if (ManualTests.TEST_IMPORTS.containsKey(feature)) {
            first.setImports(ManualTests.TEST_IMPORTS.get(feature));
        }

        System.out.println(feature);
        System.out.println("==============================================");
        System.out.println(first.getCode());
        System.out.println("==============================================");
        CompilationUnit second = CodeParser
                .parseCode(parser, first.getImports(), first.getCode(), false)
                .orElseThrow();
        System.out.println(second);
        GraphGenerator.createCanonicalizedGraphs(parser, connection, new CondExprToIfConverter(),
                                                 new CloneVisitor(), getCanonicalizers(),
                                                 "testGroup", second, feature, true
        );
        System.out.println("==============================================\n\n");
        AstTagger tagger = new AstTagger(parser);
        tagger.buildTaggedAstMessage(second, "testGroup", first.getCommitId(), true);
        System.out.println("Tagged the AST");
        System.out.println("==============================================\n\n");
    }
}
