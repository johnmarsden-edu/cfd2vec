package edu.ncsu.lab.ast_tagger;

import edu.ncsu.lab.ast_tagger.cli.Tagger;
import picocli.CommandLine;

public class App {

    public static void main(String[] args) {
        new CommandLine(new Tagger()).execute(args);
    }
}