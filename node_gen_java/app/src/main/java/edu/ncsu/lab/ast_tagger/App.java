package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.ParseProblemException;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.printer.configuration.DefaultPrinterConfiguration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.model.resolution.TypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.opencsv.bean.CsvToBeanBuilder;
import org.javatuples.Pair;
import org.jetbrains.annotations.NotNull;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class App {
    private final static int BAD_USAGE_ERROR_CODE = 1;
    private final static int BAD_DIRECTORY_PATH_ARG = 2;
    private final static int GRAPH_CREATION_FAILED = 3;

    private final static String HOSTNAME = "localhost";
    private final static int PORT = 9271;

    public static boolean verifyArgs(String[] args) {
        if (args.length < 2) {
            System.out.println(
                    "Usage: ./gradlew run --args=\"[test|analyze|generate] " + "path" + "/to/data"
                    + "/dir/1/ ... path/to/data/dir/n/\"");
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
        if (files == null) {
            return null;
        }
        for (File file : files) {
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
            System.out.println(
                    "There is no " + nameOfFolder + " folder in the " + nameOfParent + " folder.");
            System.exit(BAD_DIRECTORY_PATH_ARG);
        }
    }

    private static Optional<CompilationUnit> parseCode(CodeState cs) {
        try {
            StringBuilder source = new StringBuilder();
            for (String import_ : cs.getImports()) {
                source.append("import ").append(import_).append(";\n");
            }
            source.append("public class MethodCompiler { \n").append(cs.getCode()).append("\n}");
            return Optional.of(StaticJavaParser.parse(source.toString()));
        } catch (ParseProblemException e) {
            return Optional.empty();
        }
    }


    static final VariableCanonicalizerVisitor VARIABLE_CANONICALIZATION_CONVERTER =
            new VariableCanonicalizerVisitor();
    static final LiteralCanonicalizerVisitor LITERAL_CANONICALIZATION_CONVERTER =
            new LiteralCanonicalizerVisitor();
    static final CondExprToIfConverter condExprToIfConverter = new CondExprToIfConverter();

    private static void createGraph(ServerConnection connection, Canonicalizer canonicalizer,
                                    CompilationUnit compilationUnit, String programId,
                                    boolean debugMode) {
        try /* (var program = new FileOutputStream(programId)) */ {
            condExprToIfConverter.rewriteAllCondExprsToIf(compilationUnit);
            var message = new AstTagger(canonicalizer).buildTaggedAstMessage(compilationUnit,
                                                                             programId, debugMode
            );
            //            org.capnproto.Serialize.write(program.getChannel(), message);
            connection.send(message);
        } catch (Exception e) {
            System.err.println("Error in " + programId);
            e.printStackTrace();
        }
    }

    private static @NotNull Pair<File, File> getVerifiedFolders(File dataDir) {
        verifyDataDirIsDir(dataDir);
        String parent = dataDir.getName();
        dataDir = getDataFolder(dataDir);
        verifyFolderExists(dataDir, "Data", parent);
        File codeStatesDir = getCodeStatesFolder(dataDir);
        verifyFolderExists(codeStatesDir, "CodeStates", "Data");
        return Pair.with(dataDir, codeStatesDir);
    }

    private static void createGraphs(ServerConnection connection, Canonicalizer canonicalizer,
                                     File dataDir, boolean debugMode) {
        Pair<File, File> dirs = getVerifiedFolders(dataDir);

        var programStream = getPrograms(dirs.getValue0(), dirs.getValue1());

        programStream.ifPresent(pairStream -> pairStream.forEach(
                p -> createGraph(connection, canonicalizer, p.getValue1(), p.getValue0(),
                                 debugMode
                )));
    }

    private static long runAnalysis(File dataDir) {
        Pair<File, File> dirs = getVerifiedFolders(dataDir);
        printNumberOfStudents(dirs.getValue0());

        Optional<Stream<Pair<String, CompilationUnit>>> programStream = getPrograms(
                dirs.getValue0(), dirs.getValue1());
        if (programStream.isEmpty()) {
            return 0;
        }

        return programStream.get().count();
    }

    private static @NotNull Optional<Stream<Pair<String, CompilationUnit>>> getPrograms(File dataDir, File codeStatesDir) {
        Optional<Set<String>> validCodeStateIds = getValidCodeStateIds(dataDir);
        if (validCodeStateIds.isEmpty()) {
            return Optional.empty();
        }

        try {
            return Optional.of(new CsvToBeanBuilder<CodeState>(
                    new FileReader(new File(codeStatesDir, "CodeStates.csv")))
                                       .withType(CodeState.class)
                                       .build()
                                       .stream()
                                       .filter(cs -> !cs.getCode().isBlank())
                                       .map(cs -> Pair.with(cs.getCodeStateId(), parseCode(cs)))
                                       .filter(p -> p.getValue1().isPresent())
                                       .map(p -> Pair.with(p.getValue0(),
                                                           p.getValue1().orElseThrow()
                                       )));
        } catch (FileNotFoundException e) {
            System.out.println(
                    "The CodeStates file you are attempting to analyze doesn't exist: " + new File(
                            codeStatesDir, "CodeStates.csv").getAbsolutePath());

            return Optional.empty();
        }
    }

    private static @NotNull Optional<Set<String>> getValidCodeStateIds(File dataDir) {
        try {
            return Optional.of(new CsvToBeanBuilder<MainTableEntry>(
                    new FileReader(new File(dataDir, "MainTable.csv")))
                                       .withType(MainTableEntry.class)
                                       .build()
                                       .stream()
                                       .filter(m -> m.getEventType().equals("Compile") && m
                                               .getCompileResult()
                                               .equals("Success"))
                                       .map(MainTableEntry::getCodeStateId)
                                       .collect(Collectors.toSet()));
        } catch (FileNotFoundException e) {
            System.out.println(
                    "The MainTable file you are attempting to analyze doesn't exist: " + new File(
                            dataDir, "MainTable.csv").getAbsolutePath());
        }

        return Optional.empty();
    }

    private static void printNumberOfStudents(File dataDir) {
        try {
            File mainTable = new File(dataDir, "MainTable.csv");
            System.out.println(
                    "Number of Students for " + mainTable.getCanonicalPath() + ": " + new CsvToBeanBuilder<MainTableEntry>(
                            new FileReader(mainTable))
                            .withType(MainTableEntry.class)
                            .build()
                            .stream()
                            .map(MainTableEntry::getSubjectId)
                            .distinct()
                            .toList()
                            .size());
        } catch (IOException e) {
            System.out.println(
                    "The MainTable file you are attempting to analyze doesn't exist: " + new File(
                            dataDir, "MainTable.csv").getAbsolutePath());
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
                           
                           String test = "blue";
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
                      // save indexes of these appearances
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
        put("exceptionCatch", """
                              public void catchException()
                              {
                                try {
                                    throw new RuntimeException("Test");
                                }
                                catch (Exception e) {
                                    System.out.println("Voila");
                                }
                              }
                              """);
        put("throwUnknownMethod", """
                                  public void catchException()
                                  {
                                    try {
                                        throw UnknownMethod("Test");
                                    }
                                    catch (Exception e) {
                                        System.out.println("Voila");
                                    }
                                  }
                                  """);
        put("trFailGracefully", """
                                public int makeChocolate(int small, int big, int goal)
                                {
                                    big = 5 * big;
                                    int i;
                                    for (int n : i)
                                    {
                                        if (goal <= small) return goal;
                                        else if (goal == big) return 0;
                                        else if (goal == big / n) return 0;
                                        else
                                        {
                                            if (goal > big)
                                            {
                                                if (big + small >= goal)
                                                {
                                                    if (small >= goal - big)
                                                    {
                                                        if (big > small) return goal - big;
                                                        else return goal - small;
                                                    }
                                                    else return -1;
                                                }
                                                else return -1;
                                            }
                                            else return -1;
                                        }
                                    }
                                }
                                                                
                                                                
                                """);
        put("emptyBlockStmt", """
                              public int countEvens(int[] nums)
                              {
                                  int count = 0;
                                  for (int i = 0; i < nums.length; i++)
                                  {
                                      if (nums[i] % 2 == 0) count++;;
                                      {
                                        
                                      }
                                  }
                                  return count;
                              }
                              """);
    }};

    private static final Map<String, List<String>> testImports = new HashMap<>() {{
        put("foreach", List.of("java.util.List", "java.util.Arrays"));
    }};

    private static void exportTestGraphs(boolean runAllTests, Stream<String> features) throws IOException {
        ServerConnection connection = new ServerConnection(HOSTNAME, PORT);

        if (runAllTests) {
            testMethods.keySet().forEach(key -> generateTestGraphs(connection, key));
        } else {
            features.forEach(feature -> generateTestGraphs(connection, feature));
        }
    }

    private static final List<Pair<String, Canonicalizer>> CANONICALIZERS = new ArrayList<>() {{
        add(Pair.with("NC", new Canonicalizer.None()));
        //        add(Pair.with("VC", new Canonicalizer.Variable()));
        //        add(Pair.with("LC", new Canonicalizer.Literal()));
        //        add(Pair.with("FC", new Canonicalizer.Full()));
    }};

    public static void generateTestGraphs(ServerConnection connection, String feature) {
        CodeState first = new CodeState("Test", testMethods.get(feature));
        if (testImports.containsKey(feature)) {
            first.setImports(testImports.get(feature));
        }

        System.out.println(feature);
        System.out.println("==============================================");
        System.out.println(first.getCode());
        System.out.println("==============================================");
        CompilationUnit second = App.parseCode(first).orElseThrow();
        System.out.println(second);
        for (var canonicalizer : CANONICALIZERS) {
            App.createGraph(connection, canonicalizer.getValue1(), second,
                            canonicalizer.getValue0() + "-" + feature, true
            );
        }
        System.out.println("==============================================\n\n");

    }

    public static void generateGraphs(Stream<String> paths, boolean debugMode) {
        paths
                .map(File::new)
                .map(f -> {
                    try {
                        return Pair.with(f, new ServerConnection(HOSTNAME, PORT));
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                })
                .forEach(p -> CANONICALIZERS.forEach(
                        c -> createGraphs(p.getValue1(), c.getValue1(), p.getValue0(), debugMode)));
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

    public static void main(String[] args) throws IOException {
        verifyArgs(args);
        configureStaticJavaParser();
        switch (args[0]) {
            case "test" -> exportTestGraphs(args[1].equals("all"), Arrays.stream(args).skip(1));
            case "testCondConverter" -> testCondConverter();
            case "analyze" -> System.out.println("Total number of Code States: " + Arrays
                    .stream(args)
                    .skip(1)
                    .map(File::new)
                    .map(App::runAnalysis)
                    .reduce(0L, Long::sum));
            case "generate" -> {
                var debugMode = args[1].equals("--debug");
                generateGraphs(Arrays.stream(args).skip(debugMode ? 2 : 1).parallel(), debugMode);
            }
        }
    }

    private static void configureStaticJavaParser() {
        TypeSolver reflectionSolver = new ReflectionTypeSolver();
        JavaSymbolSolver symbolSolver = new JavaSymbolSolver(reflectionSolver);
        StaticJavaParser
                .getConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17_PREVIEW)
                .setSymbolResolver(symbolSolver)
                .setAttributeComments(false);
    }
}