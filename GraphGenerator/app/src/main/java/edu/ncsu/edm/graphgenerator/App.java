package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.google.common.base.Splitter;
import com.opencsv.bean.CsvToBeanBuilder;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.*;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class App {
    private final static int BAD_USAGE_ERROR_CODE = 1;
    private final static int BAD_DIRECTORY_PATH_ARG = 2;
    private final static int GRAPH_CREATION_FAILED = 3;
    private final static JavaParser parser = new JavaParser();

    public static boolean verifyArgs(String[] args) {
        if (args.length < 1) {
            System.out.println("Usage: ./gradlew run --args=\"path/to/data/dir/1/ ... path/to/data/dir/n/\"");
            System.exit(BAD_USAGE_ERROR_CODE);
        }

        return true;
    }

    public static void verifyDataDirIsDir(File dataDir) {
        if (!dataDir.isDirectory()) {
            System.out.println("Please provide a valid directory");
            System.exit(BAD_DIRECTORY_PATH_ARG);
        }
    }

    public static File findFolderInDir(File dir, String name) {
        File[] files = dir.listFiles();
        for (File file: files) {
            if (file.isDirectory() && file.getName().equals(name)) {
                return file;
            }
        }
        return null;
    }

    public static File getDataFolder(File dataDir) {
        return findFolderInDir(dataDir, "Data");
    }

    public static File getCodeStatesFolder(File dataDir) {
        return findFolderInDir(dataDir, "CodeStates");
    }

    public static void verifyFolderExists(File folder, String nameOfFolder, String nameOfParent) {
        if (folder == null) {
            System.out.println("There is no "+nameOfFolder+" folder in the "+nameOfParent+" folder.");
            System.exit(BAD_DIRECTORY_PATH_ARG);
        }
    }

    private static Stream<MethodDeclaration> parseMethod(CodeState cs) {
        try {
            return StaticJavaParser
                    .parse("public class MethodCompiler { \n" + cs.getCode() + "\n}")
                    .getClassByName("MethodCompiler")
                    .get()
                    .getMethods()
                    .stream();
        } catch (ParseProblemException e) {
        }

        return null;
    }

    private static Graph<FlowNode, FlowEdge> createGraph(MethodDeclaration md) {
        Graph<FlowNode, FlowEdge> graph = new DefaultDirectedGraph<>(FlowEdge.class);
        try {
            md.accept(new AstToGraphConverter(), graph);
        } catch (Exception e) {
            System.err.println(md);
            throw e;
        }
        return new AsUnmodifiableGraph<>(graph);
    }

    private static int runAnalysis(File dataDir) {
        verifyDataDirIsDir(dataDir);
        String parent = dataDir.getName();
        dataDir = getDataFolder(dataDir);
        verifyFolderExists(dataDir, "Data", parent);
        File codeStatesDir = getCodeStatesFolder(dataDir);
        verifyFolderExists(codeStatesDir, "CodeStates", "Data");
        try {
            System.out.println("Number of Students for "+
                    new File(dataDir, "MainTable.csv").getAbsolutePath() + ": "
                    + new CsvToBeanBuilder<MainTableEntry>(new FileReader(new File(dataDir, "MainTable.csv")))
                    .withType(MainTableEntry.class).build().stream()
                    .map(MainTableEntry::getSubjectId)
                    .distinct().toList().size());

            Set<String> validCodeStateIds = new CsvToBeanBuilder<MainTableEntry>(new FileReader(new File(dataDir, "MainTable.csv")))
                    .withType(MainTableEntry.class).build().stream()
                    .filter(m -> m.getEventType().equals("Compile") && m.getCompileResult().equals("Success"))
                    .map(MainTableEntry::getCodeStateId)
                    .collect(Collectors.toSet());

            try {
                List<MethodDeclaration> mds = new CsvToBeanBuilder<CodeState>(new FileReader(new File(codeStatesDir, "CodeStates.csv")))
                        .withType(CodeState.class).build().stream()
                        .filter(cs -> validCodeStateIds.contains(cs.getCodeStateId()))
                        .filter(cs -> !cs.getCode().isBlank())
                        .flatMap(App::parseMethod)
                        .filter(Objects::nonNull)
                        .toList();

                return mds.size();
            } catch (FileNotFoundException e) {
                System.out.println("The CodeStates file you are attempting to analyze doesn't exist: "
                        + new File(codeStatesDir, "CodeStates.csv").getAbsolutePath());
            }
        } catch (FileNotFoundException e) {
            System.out.println("The MainTable file you are attempting to analyze doesn't exist: "
                    + new File(dataDir, "MainTable.csv").getAbsolutePath());
        }

        return 0;
    }

    public static void exportTestGraphs(File baseDir) throws IOException {
        verifyDataDirIsDir(baseDir);
        String parent = baseDir.getName();
        File dataDir = getDataFolder(baseDir);
        verifyFolderExists(dataDir, "Data", parent);
        File codeStatesDir = getCodeStatesFolder(dataDir);
        verifyFolderExists(codeStatesDir, "CodeStates", "Data");
        CodeState first = new CodeState("Test", """
                public void example() {
                    int x = 1;
                    if (x == 2) {
                        System.out.println("x == 2");
                    } else {
                        System.out.println("x != 2");
                    }
                }
                """);

        System.out.println(first.getCode());
        MethodDeclaration second = App.parseMethod(first).findFirst().get();
        System.out.println(second.toString());
        Graph<FlowNode, FlowEdge> third = App.createGraph(second);
        System.out.println(third.toString());

        DOTExporter<FlowNode, FlowEdge> exporter = new DOTExporter<>();
        exporter.setVertexAttributeProvider(v -> Collections.singletonMap("label", DefaultAttribute.createAttribute(v.toString())));
        exporter.setEdgeAttributeProvider(e -> Collections.singletonMap("label", DefaultAttribute.createAttribute(e.toString())));
        File exportFile = new File(dataDir, "test.dot");
        System.out.println(exportFile.getCanonicalPath());
        try {
            exporter.exportGraph(third, new FileWriter(exportFile));
        } catch (IOException e) {
            System.err.println("Failed to export to "+exportFile.getAbsolutePath()+" due to "+e);
        }
    }

    public static void main(String[] args) throws IOException {
        verifyArgs(args);
        exportTestGraphs(new File(args[0]));
//        System.out.println("Total number of Method Declarations: " + Arrays.stream(args).map(File::new).map(App::runAnalysis).reduce(0, Integer::sum));
    }
}

//        List<Graph<FlowNode, FlowEdge>> cfGraphs = new CsvToBeanBuilder<CodeState>(new FileReader(new File(codeStatesDir, "CodeStates.csv")))
//                .withType(CodeState.class).build().stream()
//                .filter(cs -> validCodeStateIds.contains(cs.getCodeStateId()))
//                .filter(cs -> !cs.getCode().isBlank())
//                .map(App::parseMethod)
//                .filter(Objects::nonNull)
//                .map(App::createGraph).toList();
//
//        System.out.println("Number of graphs created: " + cfGraphs.size());





//        IfStmt ifStmt = StaticJavaParser.parseStatement("""
//if (x == y) {
//    System.out.println("x == y");
//} else if (x == y + 1) {
//    System.out.println("x == y + 1");
//} else {
//    System.out.println("x != y && x != y + 1");
//}
//""").asIfStmt()'
//        This returns true because if the else stmt is an else if then the else stmt is an if stmt.
//        System.out.println(ifStmt.getElseStmt().get().isIfStmt());