package edu.ncsu.lab.ast_tagger.cli;

import com.opencsv.CSVWriter;
import edu.ncsu.lab.ast_tagger.GraphGenerator;
import org.javatuples.Pair;
import org.javatuples.Triplet;
import picocli.CommandLine;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.stream.Stream;

@CommandLine.Command(name = "calculate", mixinStandardHelpOptions = true)
public class TaggerCalculateStats implements Runnable {

    @CommandLine.ParentCommand
    private Tagger parent;

    public TaggerCalculateStats() {
    }

    @CommandLine.Option(names = "-c", description = "code state formatted data files", split = ",")
    File[] codeStateFiles;

    @CommandLine.Option(
            names = "-p", description = "program commit formatted data files", split = ","
    )
    File[] programCommitFiles;


    @Override
    public void run() {
        List<Triplet<File, GraphGenerator.ParserMode, HashMap<String, Integer>>> stats = Stream
                .concat(
                        Arrays
                                .stream(codeStateFiles)
                                .map(f -> Pair.with(f, GraphGenerator.ParserMode.CodeState)), Arrays
                                .stream(programCommitFiles)
                                .map(f -> Pair.with(f, GraphGenerator.ParserMode.ProgramCommit)))
                .parallel()
                .map(p -> Triplet.with(p.getValue0(), p.getValue1(), GraphGenerator
                        .countGraphs(p.getValue0(), this.parent.debugMode, p.getValue1())
                        .getNumMessagesSent()))
                .toList();

        try (CSVWriter writer = new CSVWriter(new FileWriter("graph_gen_stats.csv"))) {
            for (var stat : stats) {
                for (var entry : stat.getValue2().entrySet()) {
                    writer.writeNext(
                            new String[]{stat.getValue0().getAbsolutePath(),
                                    stat.getValue1().toString(), entry.getKey(), String.valueOf(
                                    entry.getValue())});
                }
            }
        } catch (IOException exception) {
            System.err.println("Somehow I failed to create the stats file");
        }
    }
}
