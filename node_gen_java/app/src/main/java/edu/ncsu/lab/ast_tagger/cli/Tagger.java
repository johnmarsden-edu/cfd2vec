package edu.ncsu.lab.ast_tagger.cli;

import edu.ncsu.lab.ast_tagger.CanonicalizerVisitor;
import edu.ncsu.lab.ast_tagger.LiteralCanonicalizerVisitor;
import edu.ncsu.lab.ast_tagger.VariableCanonicalizerVisitor;
import org.javatuples.Pair;
import picocli.CommandLine;

import java.util.ArrayList;
import java.util.List;

// TODO: Add an analyze command that analyzes the data files provided

@CommandLine.Command(
        name = "tagger", mixinStandardHelpOptions = true, description =
        "A fully automated AST " + "Tagger for use in CFG " + "Generation", subcommands =
        {TaggerCountDuplicates.class, TaggerGenerateGraphs.class, TaggerTest.class,
                TaggerTestCondConverter.class, TaggerCalculateStats.class}
)
public class Tagger implements Runnable {

    public static List<Pair<String, List<CanonicalizerVisitor>>> getCanonicalizers() {
        return new ArrayList<>() {{
            add(Pair.with("NC", List.of()));
            add(Pair.with("VC", List.of(new VariableCanonicalizerVisitor())));
            add(Pair.with("LC", List.of(new LiteralCanonicalizerVisitor())));
            add(Pair.with("FC", List.of(new VariableCanonicalizerVisitor(),
                                        new LiteralCanonicalizerVisitor()
            )));
        }};
    }

    @CommandLine.Spec
    CommandLine.Model.CommandSpec spec;

    @CommandLine.Option(
            names = {"-d", "--debug"}, description = "run the tagger in debug mode (send the " +
                                                     "full code to the cfg generator"
    )
    boolean debugMode = false;


    @Override
    public void run() {
        throw new CommandLine.ParameterException(spec.commandLine(), "Specify a subcommand");
    }
}
