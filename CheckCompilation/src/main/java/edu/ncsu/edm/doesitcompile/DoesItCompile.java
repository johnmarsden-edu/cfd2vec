package edu.ncsu.edm.doesitcompile;

import com.opencsv.CSVWriter;
import com.opencsv.bean.CsvToBeanBuilder;
import jdk.jshell.JShell;
import jdk.jshell.SnippetEvent;

import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class DoesItCompile {
    private static final ThreadLocal<JShell> js = ThreadLocal.withInitial(JShell::create);

    public static void main(String[] args) throws IOException, InterruptedException {
        if (args.length < 1) {
            System.out.println("I need csvs to process");
            System.exit(1);
        }

        ExecutorService service = Executors.newWorkStealingPool();
        CSVWriter writer = new CSVWriter(new FileWriter("CompileableCodeStateIds.csv"));
        writer.writeNext(new String[] { "CodeStateId", "Compiles" });

        Arrays.stream(args).flatMap(f -> {
                        try {
                            return new CsvToBeanBuilder<CodeState>(new FileReader(f)).withType(CodeState.class).build().stream();
                        } catch (FileNotFoundException e) {
                            throw new RuntimeException();
                        }
                    }
                )
                .forEach(cs -> service.submit(() ->
                        writer.writeNext(new String[] {
                                cs.getCodeStateId(),
                                compiles(cs) ? "True" : "False"
                        })
                ));

        writer.close();
        if (!service.awaitTermination(1, TimeUnit.DAYS)) {
            System.out.println("It took more than a day to complete this job. Break it up or optimize this code and try again");
        }
    }

    private static boolean compiles(CodeState state) {
        JShell local = js.get();
        for (SnippetEvent e : js.get().eval(state.getCode())) {
            if (e.causeSnippet() == null) {
                switch (e.status()) {
                    case VALID:
                    case RECOVERABLE_DEFINED:
                    case DROPPED:
                    case OVERWRITTEN:
                    case NONEXISTENT:
                        break;
                    case RECOVERABLE_NOT_DEFINED:
                    case REJECTED:
                        return false;
                }
            }
        }
        local.snippets().forEach(local::drop);
        return true;
    }
}
