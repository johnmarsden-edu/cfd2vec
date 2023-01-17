package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.visitor.CloneVisitor;
import com.google.common.io.Files;
import com.opencsv.bean.CsvToBeanBuilder;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static edu.ncsu.lab.ast_tagger.CfgGenServer.getConnection;
import static edu.ncsu.lab.ast_tagger.CodeParser.createNewJavaParser;
import static edu.ncsu.lab.ast_tagger.CodeParser.parseCode;
import static edu.ncsu.lab.ast_tagger.cli.Tagger.getCanonicalizers;

public class GraphGenerator {

    public enum ParserMode {
        CodeState, ProgramCommit
    }

    public static void createCanonicalizedGraphs(JavaParser parser, ServerConnection connection,
                                                 CondExprToIfConverter condExprToIfConverter,
                                                 CloneVisitor cloner, List<Pair<String,
            List<CanonicalizerVisitor>>> canonicalizers, String programGroup,
                                                 CompilationUnit compilationUnit,
                                                 String programId, boolean debugMode) {
        try {
            condExprToIfConverter.rewriteAllCondExprsToIf(compilationUnit);
            var clone = (CompilationUnit) cloner.visit(compilationUnit, null);
            for (var stratCanPair : canonicalizers) {
                createGraph(parser, connection, stratCanPair.getValue1(),
                            stratCanPair.getValue0() + "-" + programGroup, clone, programId,
                            debugMode
                );
            }
        } catch (Exception e) {
            System.err.println("Error in " + programId);
            e.printStackTrace();
        }
    }

    public static void createGraph(JavaParser parser, ServerConnection connection,
                                   List<CanonicalizerVisitor> canonicalizers, String programGroup
            , CompilationUnit compilationUnit, String programId, boolean debugMode) {
        try /* (var program = new FileOutputStream(programId)) */ {
            for (var visitor : canonicalizers) {
                compilationUnit.accept(visitor, null);
            }
            var message = new AstTagger(parser).buildTaggedAstMessage(compilationUnit, programGroup,
                                                                      programId, debugMode
            );
            //            org.capnproto.Serialize.write(program.getChannel(), message);
            connection.send(message);
        } catch (Exception e) {
            System.err.println("Error in " + programId);
            e.printStackTrace();
        }
    }


    public static void createGraphs(File dataFile, boolean debugMode, ParserMode parserMode) {
        ServerConnection connection = getConnection();

        var parser = createNewJavaParser();
        var programStream = getPrograms(dataFile, parser, parserMode);
        String programGroup = Files.getNameWithoutExtension(dataFile.getName());
        programStream.ifPresent(pairStream -> pairStream.forEach(
                p -> createCanonicalizedGraphs(parser, connection, new CondExprToIfConverter(),
                                               new CloneVisitor(), getCanonicalizers(),
                                               programGroup, p.getValue1(), p.getValue0(), debugMode
                )));

    }


    public static @NotNull Optional<Stream<Pair<String, CompilationUnit>>> getPrograms(File dataFile, JavaParser parser, ParserMode parserMode) {
        try {
            Stream<Triplet<String, List<String>, String>> idCodePairs = Stream.of();
            boolean hasClass = false;
            if (parserMode.equals(ParserMode.ProgramCommit)) {
                idCodePairs = new CsvToBeanBuilder<ProgramFileCommit>(new FileReader(dataFile))
                        .withType(ProgramFileCommit.class)
                        .withEscapeChar('\0')
                        .build()
                        .stream()
                        .filter(pfc -> {
                            boolean hasCode = !pfc.getCode().isBlank();
                            boolean isDrawingPanel = pfc
                                    .getFileName()
                                    .endsWith("DrawingPanel.java");
                            boolean isPlannerGUI = pfc.getFileName().endsWith("PlannerGUI.java");
                            boolean isTeachingStaffRepo = pfc.getRepoName().endsWith("-TS");
                            return hasCode && !isTeachingStaffRepo && !isDrawingPanel && !isPlannerGUI;
                        })
                        .map(pfc -> Triplet.with(pfc.getCommitId(), pfc.getImports(),
                                                 pfc.getCode()
                        ));
                hasClass = true;
            } else if (parserMode.equals(ParserMode.CodeState)) {
                idCodePairs = new CsvToBeanBuilder<CodeState>(new FileReader(dataFile))
                        .withType(CodeState.class)
                        .build()
                        .stream()
                        .filter(cs -> !cs.getCode().isBlank())
                        .map(cs -> Triplet.with(cs.getCodeStateId(), cs.getImports(),
                                                cs.getCode()
                        ));
            }

            boolean finalHasClass = hasClass;
            return Optional.of(idCodePairs
                                       .map(t -> Pair.with(t.getValue0(),
                                                           parseCode(parser, t.getValue1(),
                                                                     t.getValue2(), finalHasClass
                                                           )
                                       ))
                                       .filter(p -> p.getValue1().isPresent())
                                       .map(p -> Pair.with(p.getValue0(),
                                                           p.getValue1().orElseThrow()
                                       )));

        } catch (FileNotFoundException e) {
            System.out.println(
                    "The data file you are attempting to analyze doesn't exist: " + dataFile.getAbsolutePath());

            return Optional.empty();
        } catch (Exception e) {
            System.err.println("Error when parsing " + dataFile.getAbsolutePath());
            throw e;
        }
    }
}
