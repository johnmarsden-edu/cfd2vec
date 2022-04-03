package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.opencsv.CSVWriter;
import com.opencsv.bean.CsvToBeanBuilder;
import org.javatuples.Pair;
import org.jetbrains.annotations.Nullable;
import org.jgrapht.Graph;
import org.jgrapht.graph.AsUnmodifiableGraph;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.nio.DefaultAttribute;
import org.jgrapht.nio.dot.DOTExporter;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class App {
    private final static int BAD_USAGE_ERROR_CODE = 1;
    private final static int BAD_DIRECTORY_PATH_ARG = 2;
    private final static int GRAPH_CREATION_FAILED = 3;

    public static boolean verifyArgs(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: ./gradlew run --args=\"[test|analyze|generate] path/to/data/dir/1/ ... path/to/data/dir/n/\"");
            System.exit(BAD_USAGE_ERROR_CODE);
        }

        return true;
    }

    public static void verifyDataDirIsDir(File dataDir) {
        if (!dataDir.isDirectory()) {
            System.out.println("Please provide a valid directory: " + dataDir);
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
            StringBuilder source = new StringBuilder();
            for (String import_ : cs.getImports()) {
                source.append("import ").append(import_).append(";\n");
            }
            source.append("public class MethodCompiler { \n").append(cs.getCode()).append("\n}");
            return StaticJavaParser
                    .parse( source.toString())
                    .getClassByName("MethodCompiler")
                    .get()
                    .getMethods()
                    .stream();
        } catch (ParseProblemException e) {
            return Stream.empty();
        }
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

    private static Pair<File, File> getVerifiedFolders(File dataDir) {
        verifyDataDirIsDir(dataDir);
        String parent = dataDir.getName();
        dataDir = getDataFolder(dataDir);
        verifyFolderExists(dataDir, "Data", parent);
        File codeStatesDir = getCodeStatesFolder(dataDir);
        verifyFolderExists(codeStatesDir, "CodeStates", "Data");
        return Pair.with(dataDir, codeStatesDir);
    }

    private static Stream<Pair<String, Stream<Graph<FlowNode, FlowEdge>>>> createGraphs(File dataDir) {
        Pair<File, File> dirs = getVerifiedFolders(dataDir);

        Stream<Pair<String, Stream<MethodDeclaration>>> mdStreams = getMethodDeclStreams(dirs.getValue0(), dirs.getValue1());
        if (mdStreams == null) {
            return Stream.empty();
        }

        return mdStreams.map(p -> Pair.with(p.getValue0(), p.getValue1().map(App::createGraph)));
    }

    private static int runAnalysis(File dataDir) {
        Pair<File, File> dirs = getVerifiedFolders(dataDir);
        printNumberOfStudents(dirs.getValue0());

        Stream<Pair<String, Stream<MethodDeclaration>>> mdStreams = getMethodDeclStreams(dirs.getValue0(), dirs.getValue1());
        if (mdStreams == null) {
            return 0;
        }

        return mdStreams.flatMap(Pair::getValue1).toList().size();
    }

    private static @Nullable Stream<Pair<String, Stream<MethodDeclaration>>> getMethodDeclStreams(File dataDir, File codeStatesDir) {
        Set<String> validCodeStateIds = getValidCodeStateIds(dataDir);
        if (validCodeStateIds == null) {
            return null;
        }

        try {
            return new CsvToBeanBuilder<CodeState>(new FileReader(new File(codeStatesDir, "CodeStates.csv")))
                    .withType(CodeState.class).build().stream()
                    .filter(cs -> validCodeStateIds.contains(cs.getCodeStateId()))
                    .filter(cs -> !cs.getCode().isBlank())
                    .map(cs -> Pair.with(cs.getCodeStateId(), parseMethod(cs)))
                    .filter(p -> Objects.nonNull(p.getValue0()));
        } catch (FileNotFoundException e) {
            System.out.println("The CodeStates file you are attempting to analyze doesn't exist: "
                    + new File(codeStatesDir, "CodeStates.csv").getAbsolutePath());
            return null;
        }
    }

    private static @Nullable Set<String> getValidCodeStateIds(File dataDir) {
        try {
            return new CsvToBeanBuilder<MainTableEntry>(new FileReader(new File(dataDir, "MainTable.csv")))
                    .withType(MainTableEntry.class).build().stream()
                    .filter(m -> m.getEventType().equals("Compile") && m.getCompileResult().equals("Success"))
                    .map(MainTableEntry::getCodeStateId)
                    .collect(Collectors.toSet());
        } catch (FileNotFoundException e) {
            System.out.println("The MainTable file you are attempting to analyze doesn't exist: "
                    + new File(dataDir, "MainTable.csv").getAbsolutePath());
        }

        return null;
    }

    private static void printNumberOfStudents(File dataDir) {
        try {
            System.out.println("Number of Students for "+
                    new File(dataDir, "MainTable.csv").getAbsolutePath() + ": "
                    + new CsvToBeanBuilder<MainTableEntry>(new FileReader(new File(dataDir, "MainTable.csv")))
                    .withType(MainTableEntry.class).build().stream()
                    .map(MainTableEntry::getSubjectId)
                    .distinct().toList().size());
        } catch (FileNotFoundException e) {
            System.out.println("The MainTable file you are attempting to analyze doesn't exist: "
                    + new File(dataDir, "MainTable.csv").getAbsolutePath());
        }
    }

    private static final Map<String, String> testMethods = new HashMap<>() {{
        put("if", """
                public int testIf(int num) {
                    if (num == 2) {
                        num--;
                        return num + 1;
                    } else if (num == 3) {
                        num++;
                        return 4;
                    } else {
                        num = 8;
                        return num;
                    }
                }
                """);
        put("for", """
                public void testFor(int num) {
                    for (int i = 0; i < num; i++) {
                        System.out.println(i);
                    }
                    
                    for (int i = 0, j = 10; i < j; i++, j--) {}
                    
                    for (;;i++,j++){}
                    
                    int j = 0;
                    for (; j < num; j++) {
                        System.out.println(j);
                    }
                    
                    int k = 0;
                    for (; k < num;) {
                        System.out.println(k);
                        k++;
                    }
                    
                    int u = 0;
                    for (;;) {
                        System.out.println(u);
                        u++;
                    }
                }
                """);
        put("while", """
                public void testWhile(int num) {
                    int i = 0;
                    while (i < num) {
                        System.out.println(i);
                        i++;
                    }
                    
                    while(true) {
                        System.out.println("Infinite loop");
                    }
                }
                """);
        put("foreach", """
                public void testForeach() {
                    int[] n = { 1, 2, 3, 4, 5, 6, 7, 8, 9, 10 };
                    List<Integer> x = new ArrayList<Integer>();
                    for (int i : n) {
                        x.add(i);
                    }
                   
                    for (int i : x) {
                        System.out.println(i);
                        break;
                    }
                    
                    String test = "blee";
                    for (char character : test) {
                        System.out.println(character);
                    }
                }
                """);
        put("labeled", """
                public boolean bobThere(String str) {
                    String postB;
                    int index = -1;
                    loop: for (int i = -1; i <= str.length(); i++) {
                        if (str.startsWith("b")) {
                            postB = str.substring(0);
                            index = postB.indexOf("b");
                            if (index == 1) {
                                break loop;
                            }
                        }
                    }
                    if (index == 1) {
                        return true;
                    } else {
                        return false;
                    }
                }
                """);
    }};

    private static final Map<String, List<String>> testImports = new HashMap<>() {{
       put("foreach", List.of("java.util.List", "java.util.Arrays"));
    }};

    public static void exportTestGraphs(String feature) {
        CodeState first = new CodeState("Test", testMethods.get(feature));
        if (testImports.containsKey(feature)) {
            first.setImports(testImports.get(feature));
        }

        System.out.println(first.getCode());
        MethodDeclaration second = App.parseMethod(first).findFirst().get();
        System.out.println(second);
        Graph<FlowNode, FlowEdge> third = App.createGraph(second);
        System.out.println(third);

        DOTExporter<FlowNode, FlowEdge> exporter = new DOTExporter<>();
        exporter.setVertexAttributeProvider(v -> Collections.singletonMap("label", DefaultAttribute.createAttribute(v.toString())));
        exporter.setEdgeAttributeProvider(e -> Collections.singletonMap("label", DefaultAttribute.createAttribute(e.toString())));
        File exportFile = new File("test.dot");
        try {
            exporter.exportGraph(third, new FileWriter(exportFile));
        } catch (IOException e) {
            System.err.println("Failed to export to "+exportFile.getAbsolutePath()+" due to "+e);
        }
    }

    public static void generateGraphs(Stream<String> paths) {
        try {
            FileWriter nodeFile = new FileWriter("Nodes.csv");
            CSVWriter nodeCsv = new CSVWriter(nodeFile);
            nodeCsv.writeNext(new String[] { "CodeStateId", "NodeId", "NodeData" });

            FileWriter edgeFile = new FileWriter("Edges.csv");
            CSVWriter edgeCsv = new CSVWriter(edgeFile);
            edgeCsv.writeNext(new String[]{ "CodeStateId", "Node1Id", "Node2Id", "EdgeData" });
            try {
                paths.map(File::new).flatMap(App::createGraphs).limit(100).forEach(
                        p -> {
                            List<String[]> nodeLines = new ArrayList<>();
                            List<String[]> edgeLines = new ArrayList<>();
                            p.getValue1().forEach(
                                    g -> {
                                        int id = 0;
                                        Map<FlowNode, String> nodeIds = new HashMap<>();
                                        for (FlowNode n: g.vertexSet()) {
                                            String idStr = String.valueOf(id);
                                            nodeIds.put(n, idStr);
                                            nodeLines.add(new String[]{
                                                    p.getValue0(),
                                                    idStr,
                                                    n.toString()
                                            });
                                            id += 1;
                                        }

                                        for (FlowEdge e: g.edgeSet()) {
                                            String incomingId = nodeIds.get(g.getEdgeSource(e));
                                            String outgoingId = nodeIds.get(g.getEdgeTarget(e));
                                            edgeLines.add(new String[]{
                                                    p.getValue0(),
                                                    incomingId,
                                                    outgoingId,
                                                    e.toString()
                                            });
                                        }
                                    }
                            );
                            nodeCsv.writeAll(nodeLines);
                            edgeCsv.writeAll(edgeLines);
                        }
                );
            }
            finally {
                nodeCsv.close();
                edgeCsv.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        verifyArgs(args);
        configureStaticJavaParser();
        switch (args[0]) {
            case "test" -> exportTestGraphs(args[1]);
            case "analyze" -> System.out.println("Total number of Method Declarations: " + Arrays.stream(args).skip(1)
                    .map(File::new).map(App::runAnalysis).reduce(0, Integer::sum));
            case "generate" -> generateGraphs(Arrays.stream(args).skip(1));
        }
    }

    private static void configureStaticJavaParser() {
        TypeSolver reflectionSolver = new ReflectionTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(reflectionSolver);
        StaticJavaParser.getConfiguration().setSymbolResolver(symbolSolver);
    }
}