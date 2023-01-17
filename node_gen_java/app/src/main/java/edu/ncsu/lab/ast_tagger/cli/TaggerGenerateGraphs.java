package edu.ncsu.lab.ast_tagger.cli;

import edu.ncsu.lab.ast_tagger.GraphGenerator;
import org.javatuples.Pair;
import picocli.CommandLine;

import java.io.File;
import java.util.Arrays;
import java.util.stream.Stream;

@CommandLine.Command(name = "generate", mixinStandardHelpOptions = true)
public class TaggerGenerateGraphs implements Runnable {

    @CommandLine.ParentCommand
    private Tagger parent;

    public TaggerGenerateGraphs() {
    }

    @CommandLine.Option(names = "-c", description = "code state formatted data files", split = ",")
    File[] codeStateFiles;

    @CommandLine.Option(
            names = "-p", description = "program commit formatted data files", split = ","
    )
    File[] programCommitFiles;


    @Override
    public void run() {
        Stream
                .concat(
                        Arrays
                                .stream(codeStateFiles)
                                .map(f -> Pair.with(f, GraphGenerator.ParserMode.CodeState)), Arrays
                                .stream(programCommitFiles)
                                .map(f -> Pair.with(f, GraphGenerator.ParserMode.ProgramCommit)))
                .parallel()
                .forEach(p -> GraphGenerator.createGraphs(p.getValue0(), this.parent.debugMode,
                                                          p.getValue1()
                ));
    }
}
