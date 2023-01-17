package edu.ncsu.lab.ast_tagger.cli;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import edu.ncsu.lab.ast_tagger.CondExprToIfConverter;
import picocli.CommandLine;

@CommandLine.Command(
        name = "testCondConverter", mixinStandardHelpOptions = true, description = "Test that the" +
                                                                                   " conditional " +
                                                                                   "expression " +
                                                                                   "converter " +
                                                                                   "works correctly"
)
public class TaggerTestCondConverter implements Runnable {

    public TaggerTestCondConverter() {

    }

    @Override
    public void run() {
        String testJava = """
                          import java.util.stream.IntStream;
                                          
                          public class TestCondConverter {
                              public static void main() {
                                  int x = 5;
                                  int j = x % 2 == 1 ? x % 8 == 3? 1 : 8 : 1;
                                          
                                  int foo = switch (j) {
                                      case 3 -> 4;
                                      case 12 -> x % 7 == 2 ? 4 : 10;
                                      default -> 17;
                                  };
                                          
                                  int bar = IntStream.of(1, 2, 3, 4, 5).map(i -> i % 2 == 0 ? i * 2 : i * 3).sum();
                              }
                          }
                          """;

        CompilationUnit cu = StaticJavaParser.parse(testJava);
        CondExprToIfConverter condExprToIfConverter = new CondExprToIfConverter();
        condExprToIfConverter.rewriteAllCondExprsToIf(cu);
        System.out.println(cu.toString(new DefaultPrinterConfiguration()));
    }
}
