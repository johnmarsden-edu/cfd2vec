package edu.ncsu.lab.ast_tagger.cli;

import com.github.javaparser.JavaParser;
import com.google.common.io.Files;
import edu.ncsu.lab.ast_tagger.AstTagger;
import edu.ncsu.lab.ast_tagger.GraphGenerator;
import org.javatuples.Pair;
import picocli.CommandLine;

import java.io.File;
import java.util.Arrays;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import static edu.ncsu.lab.ast_tagger.CodeParser.createNewJavaParser;
import static edu.ncsu.lab.ast_tagger.GraphGenerator.getPrograms;

@CommandLine.Command(name = "countDuplicates", mixinStandardHelpOptions = true)
public class TaggerCountDuplicates implements Runnable {
    public TaggerCountDuplicates() {
    }

    @CommandLine.Option(names = "-c", description = "code state formatted data files", split = ",")
    File[] codeStateFiles;

    @CommandLine.Option(
            names = "-p", description = "program commit formatted data files", split = ","
    )
    File[] programCommitFiles;


    private static final ConcurrentHashMap<String, Integer> codeStateIds =
            new ConcurrentHashMap<>();


    private static void countDuplicates(File dataFile, GraphGenerator.ParserMode parserMode) {
        System.out.println("Counting duplicates in " + dataFile);

        JavaParser parser = createNewJavaParser();
        String programGroup = Files.getNameWithoutExtension(dataFile.getName());
        var programStream = getPrograms(dataFile, parser, parserMode);
        AtomicReference<String> currentId = new AtomicReference<>("");
        try {
            programStream.ifPresent(pairStream -> pairStream.forEach(p -> {
                AstTagger tagger = new AstTagger(parser);
                tagger.buildTaggedAstMessage(p.getValue1(), programGroup, p.getValue0(), true);
                currentId.set(p.getValue0());
                codeStateIds.putIfAbsent(p.getValue0(), 0);
                codeStateIds.put(p.getValue0(), codeStateIds.get(p.getValue0()));
            }));
        } catch (Exception e) {
            System.err.println(
                    "Error occurred in this file: " + dataFile.getAbsolutePath() + "with a " +
                    "program with this ID: " + currentId.get());
            throw e;
        }
    }

    @Override
    public void run() {
        System.out.println("Starting to count duplicates");
        if (codeStateFiles == null) {
            codeStateFiles = new File[]{};
        }
        if (programCommitFiles == null) {
            programCommitFiles = new File[]{};
        }
        Stream
                .concat(Arrays
                                .stream(codeStateFiles)
                                .map(f -> Pair.with(f, GraphGenerator.ParserMode.CodeState)), Arrays
                                .stream(programCommitFiles)
                                .map(f -> Pair.with(f, GraphGenerator.ParserMode.ProgramCommit)))
                .forEach(p -> countDuplicates(p.getValue0(), p.getValue1()));

        System.out.println("Found " + codeStateIds.size() + " commit IDs");
        var duplicates = codeStateIds
                .entrySet()
                .stream()
                .filter(entry -> entry.getValue() > 1)
                .toList();

        System.out.println("Printing any duplicates found: ");
        for (var duplicate : duplicates) {
            System.out.println(
                    "commit state " + duplicate.getKey() + " has " + duplicate.getValue() + " " + "duplicate programs");
        }
    }
}
