package edu.ncsu.edm.graphgenerator;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
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
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
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


    static final VariableCanonicalizationConverter VARIABLE_CANONICALIZATION_CONVERTER = new VariableCanonicalizationConverter();
    static final LiteralCanonicalizationConverter LITERAL_CANONICALIZATION_CONVERTER = new LiteralCanonicalizationConverter();
    static final AstToGraphConverter astToGraphConverter = new AstToGraphConverter();
    static final CondExprToIfConverter condExprToIfConverter = new CondExprToIfConverter();

    private static Graph<FlowNode, FlowEdge> createGraph(MethodDeclaration md) {
        Graph<FlowNode, FlowEdge> graph = new DefaultDirectedGraph<>(FlowEdge.class);
        try {
            condExprToIfConverter.rewriteAllCondExprsToIf(md);
            md.accept(astToGraphConverter, graph);
        } catch (Exception e) {
            System.err.println(e);
            return null;
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

        return mdStreams.map(p -> Pair.with(p.getValue0(), p.getValue1().map(App::createGraph).filter(Objects::nonNull)));
    }

    private static int runAnalysis(File dataDir) {
        Pair<File, File> dirs = getVerifiedFolders(dataDir);
        printNumberOfStudents(dirs.getValue0());

        Stream<Pair<String, Stream<MethodDeclaration>>> mdStreams = getMethodDeclStreams(dirs.getValue0(), dirs.getValue1());
        if (mdStreams == null) {
            return 0;
        }

        return mdStreams.filter(p -> p.getValue1().findFirst().isPresent()).toList().size();
    }

    private static @Nullable Stream<Pair<String, Stream<MethodDeclaration>>> getMethodDeclStreams(File dataDir, File codeStatesDir) {
        Set<String> validCodeStateIds = getValidCodeStateIds(dataDir);
        if (validCodeStateIds == null) {
            return null;
        }

        try {
            return new CsvToBeanBuilder<CodeState>(new FileReader(new File(codeStatesDir, "CodeStates.csv")))
                    .withType(CodeState.class).build().stream()
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
            File mainTable =  new File(dataDir, "MainTable.csv");
            System.out.println("Number of Students for "+
                   mainTable.getCanonicalPath() + ": "
                    + new CsvToBeanBuilder<MainTableEntry>(new FileReader(mainTable))
                    .withType(MainTableEntry.class).build().stream()
                    .map(MainTableEntry::getSubjectId)
                    .distinct().toList().size());
        } catch (IOException e) {
            System.out.println("The MainTable file you are attempting to analyze doesn't exist: "
                    + new File(dataDir, "MainTable.csv").getAbsolutePath());
        }
    }

    private static final Map<String, String> testMethods = new HashMap<>() {{
        put("if", """
                public int testIf(String test) {
                    int num = Integer.parseInt(test);
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
                    
                    String result = "";
                    boolean control = true;
                    int index = 0;
                    int bread = 0;
                    int secondBread = 0;
                    while (control) {
                        bread = str.indexOf("bread", index);
                        if ((bread) + 5 == str.length()) {
                            break;
                        } else if (bread == -1) {
                            break;
                        }
                        index = bread + 5;
                        secondBread = str.indexOf("bread", index);
                        if (secondBread == -1) {
                            break;
                        }
                        if (control) {
                            result = str.substring(index, secondBread);
                        }
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
                    boolean isBalanced = false;
                    for (int i = 0; i < str.length(); i++) {
                        OUTER_LOOP: if (str.charAt(i) == 'x') {
                            for (int j = i; j < str.length(); j++) {
                                if (str.charAt(j) == 'y') {
                                    isBalanced = true;
                                    break OUTER_LOOP;
                                } else {
                                    isBalanced = false;
                                }
                            }
                        }
                    }
                
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
        put("switch", """
                public String alarmClock(int day, boolean vacation) {
                    String alarm = "";
                    if (vacation) {
                        switch(day) {
                            case 0:
                                alarm = "off";
                                break;
                            case 1:
                                alarm = "10:00";
                                break;
                            case 2:
                                alarm = "10:00";
                                break;
                            case 3:
                                alarm = "10:00";
                                break;
                            case 4:
                                alarm = "10:00";
                                break;
                            case 5:
                                alarm = "10:00";
                                break;
                            case 6:
                                alarm = "off";
                                break;
                        }
                    } else {
                        switch(day) {
                            case 0:
                                alarm = "10:00";
                                break;
                            case 1:
                                alarm = "7:00";
                                break;
                            case 2:
                                alarm = "7:00";
                                break;
                            case 3:
                                alarm = "7:00";
                                break;
                            case 4:
                                alarm = "7:00";
                                break;
                            case 5:
                                alarm = "7:00";
                                break;
                            case 6:
                                alarm = "10:00";
                                break;
                            default:
                                alarm = "6:00";
                                break;
                        }
                    }
                    return alarm;
                }
                """);
        put("return", """
                public static boolean testReturn() {
                    int x = 12;
                    if (x == 13) {
                        return false;
                    }
                    
                    x = 7;
                    return true;
                }
                """);
        put("break", """
                public String testBreak(String str, String word) {
                    // find all appearances of word in str
                    // save indeces of these appearances
                    // split each part into a new substring
                    String str0 = "";
                    String str1 = "";
                    String plus = "";
                    String plus1 = "";
                    int y = 0;
                    while (str.contains(word)) {
                        int x = str.indexOf(word);
                        if (y == x) {
                            break;
                        }
                        str1 = str.substring(y, x);
                        for (int i = 0; i < str1.length(); i++) {
                            plus1 = plus1.concat("+");
                        }
                        plus = plus.concat(plus1);
                        plus = plus.concat(word);
                        str = str.substring(x + word.length());
                        y = x;
                        // return str0;
                    }
                    for (int i = 0; i < str.length(); i++) {
                        plus = plus.concat("+");
                    }
                    return plus;
                }
                """);
        put("continue", """
                public int[] zeroMax(int[] nums) {
                    int numsLength = nums.length;
                    int[] result = new int[numsLength];
                    for (int index = 0; index < numsLength; ++index) {
                        int currentValue = nums[index];
                        if (currentValue != 0) {
                            result[index] = currentValue;
                            continue;
                        }
                        int largestOdd = 0;
                        for (int oddIndex = index + 1; oddIndex < numsLength; ++oddIndex) {
                            int currentOddValue = nums[oddIndex];
                            if (currentOddValue % 2 > 0) {
                                if (largestOdd < currentOddValue) {
                                    largestOdd = currentOddValue;
                                }
                            }
                        }
                        result[index] = largestOdd;
                    }
                    return result;
                }
                """);
        put("aggregateWhile", """
                public int aggregate(int[] array)
                {
                    int sum = 0;
                    int current = 0;
                    while (current < array.length) {
                        sum += array[current];
                        current += 1;
                    }
                    return sum;
                }
                                
                """);
        put("aggregateFor", """
                public int aggregate(int[] array)
                {
                    int sum = 0;
                    for (int i = 0; i < array.length; i += 1)
                    {
                        sum += array[i];
                    }
                    return sum;
                }
                """);
    }};

    private static final Map<String, List<String>> testImports = new HashMap<>() {{
       put("foreach", List.of("java.util.List", "java.util.Arrays"));
    }};

    private static void exportTestGraphs(boolean runAllTests, Stream<String> features) {
        if (runAllTests) {
            testMethods.keySet().forEach(App::generateTestGraphs);
        } else {
            features.forEach(App::generateTestGraphs);
        }
    }

    public static void generateTestGraphs(String feature) {
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
        File exportFile = new File(feature + ".dot");
        try {
            exporter.exportGraph(third, new FileWriter(exportFile));
        } catch (IOException e) {
            System.err.println("Failed to export to "+exportFile.getAbsolutePath()+" due to "+e);
        }
    }

    private static class CanonicalizationStrategy {
        private final CSVWriter nodes;
        private final CSVWriter edges;
        private final CSVWriter stats;
        private final Consumer<Graph<FlowNode, FlowEdge>> canonicalizer;

        private final List<String[]> nodeLines;
        private final List<String[]> edgeLines;

        private final AtomicInteger numGraphs = new AtomicInteger(0);
        private final AtomicInteger numCodeStates = new AtomicInteger(0);

        public CanonicalizationStrategy(String strategy) throws IOException {
            this(strategy, null);
        }

        public CanonicalizationStrategy(String strategy, Consumer<Graph<FlowNode, FlowEdge>> canonicalizer) throws IOException {String nodeName = strategy + "Nodes.csv";
            String edgeName = strategy + "Edges.csv";
            String statName = strategy + "Stats.csv";


            FileWriter nodeFile = null;
            try {
                nodeFile = new FileWriter(nodeName);
            } catch (IOException ignored) {
                throw new RuntimeException("Couldn't create " + nodeName);
            }
            CSVWriter nodeCsv = new CSVWriter(nodeFile);
            nodeCsv.writeNext(new String[] { "CodeStateId", "MethodNum", "NodeId", "NodeData" });

            FileWriter edgeFile = null;
            try {
                edgeFile = new FileWriter(edgeName);
            } catch (IOException e) {
                nodeFile.close();
                throw new RuntimeException("Couldn't create " + edgeName);
            }
            CSVWriter edgeCsv = new CSVWriter(edgeFile);
            edgeCsv.writeNext(new String[] { "CodeStateId", "MethodNum", "Node1Id", "Node2Id", "EdgeData" });

            FileWriter statsFile = null;
            try {
                statsFile = new FileWriter(statName);
            } catch (IOException e) {
                nodeFile.close();
                edgeFile.close();
                throw new RuntimeException("Couldn't create " + statName);
            }
            CSVWriter statsCsv = new CSVWriter(statsFile);
            statsCsv.writeNext(new String[] { "Number of Graphs", "Number of CodeStates", "Number of Methods with ConditionalExpr"});
            this.nodes = nodeCsv;
            this.edges = edgeCsv;
            this.stats = statsCsv;
            this.canonicalizer = canonicalizer;
            this.nodeLines = new ArrayList<>();
            this.edgeLines = new ArrayList<>();
        }

        public void close() throws IOException {
            stats.writeNext(new String[] { numGraphs.toString(), numCodeStates.toString(), CondExprToIfConverter.numberOfMethodsWithCondExpr.toString() });
            stats.close();
            nodes.close();
            edges.close();
        }

        public void addLinesForGraph(String codeStateId,
                                     Graph<FlowNode, FlowEdge> g,
                                     AtomicInteger currentCodeStateCountedMethods) {

            if (this.canonicalizer != null) {
                this.canonicalizer.accept(g);
            }

            if (currentCodeStateCountedMethods.get() == 0) {
                this.numCodeStates.incrementAndGet();
            }

            String nm = Integer.toString(currentCodeStateCountedMethods.incrementAndGet());
            this.numGraphs.incrementAndGet();
            Map<FlowNode, String> nodeIds = new HashMap<>();
            int id = 0;
            for (FlowNode n: g.vertexSet()) {
                String idStr = String.valueOf(id);
                nodeIds.put(n, idStr);
                this.nodeLines.add(new String[]{
                        codeStateId,
                        nm,
                        idStr,
                        n.toString()
                });
                id += 1;
            }

            for (FlowEdge e: g.edgeSet()) {
                String incomingId = nodeIds.get(g.getEdgeSource(e));
                String outgoingId = nodeIds.get(g.getEdgeTarget(e));
                this.edgeLines.add(new String[]{
                        codeStateId,
                        nm,
                        incomingId,
                        outgoingId,
                        e.toString()
                });
            }
        }

        public void writeLines() {
            this.nodes.writeAll(this.nodeLines);
            this.edges.writeAll(this.edgeLines);
            this.nodeLines.clear();
            this.edgeLines.clear();
        }
    }

    private record CanonicalizationStrategyCollection(CanonicalizationStrategy... strategies) {
        public boolean close() {
            boolean allClosed = true;
            for (CanonicalizationStrategy strategy : this.strategies) {
                try {
                    strategy.close();
                } catch (IOException e) {
                    allClosed = false;
                }
            }
            return allClosed;
        }

        public boolean write(String codeStateId, Stream<Graph<FlowNode, FlowEdge>> graphs) {
            AtomicBoolean allWritten = new AtomicBoolean(true);
            AtomicInteger currentCodeStateCountedMethods = new AtomicInteger(0);
            graphs.forEach(
                g -> {
                    for (CanonicalizationStrategy strategy : this.strategies) {
                        try {
                            currentCodeStateCountedMethods.set(0);
                            strategy.addLinesForGraph(codeStateId, g, currentCodeStateCountedMethods);
                            strategy.writeLines();
                        } catch (Exception e) {
                            e.printStackTrace();
                            allWritten.set(false);
                        }
                    }
                }
            );
            return allWritten.get();
        }
    }

    public static void generateGraphs(Stream<String> paths) {
        try {
            CanonicalizationStrategy noCanonicalization = new CanonicalizationStrategy("noneCanonicalization");
            CanonicalizationStrategy partialCanonicalization = new CanonicalizationStrategy("partCanonicalization",
                    g -> g.vertexSet().forEach(fn -> fn.getNode().ifPresent(n -> {
                        n.accept(VARIABLE_CANONICALIZATION_CONVERTER, null);
                    })));
            CanonicalizationStrategy fullCanonicalization = new CanonicalizationStrategy("fullCanonicalization",
                    g -> g.vertexSet().forEach(fn -> fn.getNode().ifPresent(n -> {
                        n.accept(LITERAL_CANONICALIZATION_CONVERTER, null);
                    })));
            CanonicalizationStrategyCollection strategies = new CanonicalizationStrategyCollection(
                    noCanonicalization,
                    partialCanonicalization,
                    fullCanonicalization
            );
            try {
                paths.map(File::new).flatMap(App::createGraphs).forEach(p -> strategies.write(p.getValue0(), p.getValue1()));
            } finally {
                strategies.close();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void testCondConverter() {
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

    public static void main(String[] args) {
        verifyArgs(args);
        configureStaticJavaParser();
        switch (args[0]) {
            case "test" -> exportTestGraphs(args[1].equals("all"), Arrays.stream(args).skip(1));
            case "testCondConverter" -> testCondConverter();
            case "analyze" -> System.out.println("Total number of Code States: " + Arrays.stream(args).skip(1)
                    .map(File::new).map(App::runAnalysis).reduce(0, Integer::sum));
            case "generate" -> generateGraphs(Arrays.stream(args).skip(1));
        }
    }

    private static void configureStaticJavaParser() {
        TypeSolver reflectionSolver = new ReflectionTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(reflectionSolver);
        StaticJavaParser.getConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17_PREVIEW)
                .setSymbolResolver(symbolSolver)
                .setAttributeComments(false);
    }
}