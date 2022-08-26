package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.BinaryExpr;
import com.github.javaparser.ast.expr.BooleanLiteralExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.LambdaExpr;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.ReferenceType;
import com.github.javaparser.resolution.UnsolvedSymbolException;
import com.github.javaparser.resolution.declarations.ResolvedReferenceTypeDeclaration;
import com.github.javaparser.resolution.types.ResolvedReferenceType;
import com.github.javaparser.resolution.types.ResolvedType;
import com.github.javaparser.resolution.types.parametrization.ResolvedTypeParametersMap;
import edu.ncsu.lab.cfg_gen.api.CfgGenerator;
import org.capnproto.MessageBuilder;
import org.capnproto.Text;
import org.capnproto.TextList;
import org.capnproto.Void;
import org.javatuples.Pair;

import java.util.*;
import java.util.stream.Stream;

/**
 * This class when initiated is responsible for handling the tagging of an AST into the
 * Cap'n Proto format generated from the message.capnp in the schema directory in the top-level
 * directory of this repository.
 * <p>
 * TODOS
 * TODO: Handle the creation of LambdaExprs in some way
 * TODO: Add documentation to every method in this class
 *
 * @author John Marsden
 */
public class AstTagger {
    private final Canonicalizer canonicalizer;
    private int numIndexes = 0;
    private int numIterators = 0;

    public AstTagger() {
        this(new Canonicalizer.None());
    }

    public AstTagger(Canonicalizer canonicalizer) {
        this.canonicalizer = canonicalizer;
    }

    /**
     * The entry method to tag and build the Cap'n Proto message
     *
     * @param compilationUnit The source AST to convert into a Cap'n Proto message
     * @param programId       The program ID to send over the wire
     * @param debugMode       Whether to send the program over the wire
     * @return The Cap'n Proto message to send over the wire
     */
    public org.capnproto.MessageBuilder buildTaggedAstMessage(CompilationUnit compilationUnit,
                                                              String programId, boolean debugMode) {
        org.capnproto.MessageBuilder capnpMessageBuilder = new MessageBuilder();
        var messageBuilder = capnpMessageBuilder.initRoot(CfgGenerator.Message.factory);

        List<Pair<String, MethodDeclaration>> methods = new ArrayList<>();
        List<ClassOrInterfaceDeclaration> classes = compilationUnit
                .findAll(ClassOrInterfaceDeclaration.class)
                .stream()
                .filter(c -> !c.isInterface())
                .toList();

        for (var klass : classes) {
            var namespace = klass.getFullyQualifiedName().orElse("");
            for (MethodDeclaration method : klass.getMethods()) {
                methods.add(Pair.with(namespace, method));
            }
        }

        messageBuilder.setProgramId(programId);
        var methodsBuilder = messageBuilder.initMethods(methods.size());
        var methodsBuilderIt = methodsBuilder.iterator();
        var methodsIt = methods.iterator();
        while (methodsBuilderIt.hasNext() && methodsIt.hasNext()) {
            var method = methodsIt.next();
            var methodBuilder = methodsBuilderIt.next();
            this.buildMethod(method.getValue1(), method.getValue0(), methodBuilder);
        }

        var debugModeBuilder = messageBuilder.initDebug();
        if (debugMode) {
            debugModeBuilder.setSome(compilationUnit.toString());
        } else {
            debugModeBuilder.setNone(Void.VOID);
        }

        return capnpMessageBuilder;
    }

    // Function block handlers

    /**
     * Handles building the Method declarations
     *
     * @param method    The method to build a function block for
     * @param namespace The namespace to put this Method under
     * @param builder   The AST Node builder that this function will populate
     */
    private void buildMethod(MethodDeclaration method, String namespace,
                             CfgGenerator.AstNode.Builder builder) {
        // Set the node type to method declaration
        builder.setNodeType("MethodDeclaration");
        builder.initLabel().setNone(Void.VOID);

        // Set the contents to be a function block node
        var methodBuilder = builder.initContents().initFunctionBlock();

        // Set the name of the function block node to be the method name
        var methodNameBuilder = methodBuilder.initName();
        methodNameBuilder.setSome(method.getNameAsString());

        // Set the namespace parameters
        String[] splitNamespace = namespace.split("\\.");
        var namespaceBuilder = methodBuilder.initNamespace(splitNamespace.length);
        for (int j = 0; j < splitNamespace.length; j++) {
            namespaceBuilder.set(j, new Text.Reader(splitNamespace[j]));
        }

        // Initialize the block nodes
        var blockBuilder = methodBuilder.initBlock();
        Optional<BlockStmt> block = method.getBody();
        String[] methodNamespace = Arrays.copyOf(splitNamespace, splitNamespace.length + 1);
        methodNamespace[methodNamespace.length - 1] = method.getNameAsString();
        this.buildBlock(block, blockBuilder, methodNamespace);

        // Set the parameters of the function block node to be the parameters of the method
        var paramBuilder = methodBuilder.initParameters(method.getParameters().size());

        int i = 0;
        for (var param : method.getParameters()) {
            paramBuilder.set(i, new Text.Reader(this.canonicalizer.canonicalize(param)));
            i++;
        }
    }

    private void buildAnonymousFunction(LambdaExpr lambdaExpr,
                                        CfgGenerator.FunctionBlock.Builder functionBuilder,
                                        String[] methodNamespace) {

        var namespaceBuilder = functionBuilder.initNamespace(methodNamespace.length);
        for (int j = 0; j < methodNamespace.length; j++) {
            namespaceBuilder.set(j, new Text.Reader(methodNamespace[j]));
        }

        functionBuilder.initName().setNone(Void.VOID);

        var blockBuilder = functionBuilder.initBlock();

        methodNamespace = Arrays.copyOf(methodNamespace, methodNamespace.length + 1);
        methodNamespace[methodNamespace.length - 1] = "lambda";
        this.buildBlock(Optional.of(lambdaExpr.getBody()), blockBuilder, methodNamespace);

        var parametersBuilders = functionBuilder.initParameters(lambdaExpr.getParameters().size());

        int i = 0;
        for (var param : lambdaExpr.getParameters()) {
            parametersBuilders.set(i, new Text.Reader(this.canonicalizer.canonicalize(param)));
            i++;
        }
    }

    // Loop handlers

    /**
     * Handles building the Loop Block with all the appropriate information
     *
     * @param nodeBuilder                    The node builder to populate with data
     * @param block                          The block or body of the loop
     * @param condition                      The condition for the loop to stop
     * @param initializations                The initialization steps to take prior to starting
     *                                       the looping
     * @param updates                        The update steps to take at the end of a loop before
     *                                       checking the condition
     * @param checkConditionOnFirstIteration Whether you should check the condition on the
     *                                       first iteration or not allowing you to create do-while
     *                                       loops (and do-for loops though those don't exist)
     * @param methodNamespace                The method namespace for this loop block
     */
    private void buildLoopBlock(CfgGenerator.AstNode.Builder nodeBuilder, Optional<?
            extends Statement> block, Optional<Expression> condition,
                                List<? extends Node> initializations, List<Expression> updates,
                                boolean checkConditionOnFirstIteration, String[] methodNamespace) {
        nodeBuilder.setBreakable(true);
        var loopBuilder = nodeBuilder.initContents().initLoopBlock();
        // Set whether we check the condition on the first iteration
        loopBuilder.setFirstIterationConditionCheck(checkConditionOnFirstIteration);

        // If the block is empty or a block statement pass it to the block builder method
        // Otherwise, build a single node for the single statement that can be underneath a
        // Loop
        var blockBuilder = loopBuilder.initBlock();
        this.buildBlock(block, blockBuilder, methodNamespace);

        // Build the conditions
        var conditionBuilder = loopBuilder.initCondition();
        if (condition.isPresent()) {
            this.buildCondition(condition.get(), conditionBuilder);
        } else {
            conditionBuilder.setEmpty(Void.VOID);
        }

        // Build the initialization steps
        var initializationBuilder = loopBuilder.initInitialization(initializations.size());
        this.buildTextList(initializations, initializationBuilder);

        // Build the update steps
        var updateBuilder = loopBuilder.initUpdate(updates.size());
        this.buildTextList(updates, updateBuilder);
    }

    /**
     * Build a Do statement loop block
     *
     * @param doStmt          The do statement to generate a loop block for
     * @param nodeBuilder     The node builder to populate with the loop block
     * @param methodNamespace The method namespace for this do statement
     */
    private void buildDoStmt(DoStmt doStmt, CfgGenerator.AstNode.Builder nodeBuilder,
                             String[] methodNamespace) {
        this.buildLoopBlock(nodeBuilder, Optional.of(doStmt.getBody()),
                            Optional.of(doStmt.getCondition()), List.of(), List.of(), false,
                            methodNamespace
        );
    }


    private void buildForEachStmt(ForEachStmt forEachStmt,
                                  CfgGenerator.AstNode.Builder nodeBuilder,
                                  String[] methodNamespace) {
        Optional<ResolvedType> iterableType;
        try {
            iterableType = Optional.of(forEachStmt.getIterable().calculateResolvedType());
        } catch (RuntimeException ignored) {
            iterableType = Optional.empty();
        }

        Optional<ResolvedReferenceTypeDeclaration> refType = Optional.empty();
        if (iterableType.isPresent() && iterableType.get().isReferenceType()) {
            refType = iterableType.get().asReferenceType().getTypeDeclaration();
        }


        var variable = forEachStmt.getVariable().getVariables().getFirst().orElseThrow();
        var varType = variable.getTypeAsString();
        var varName = variable.getNameAsString();

        if (iterableType.isEmpty() || iterableType.get().isArray() || !iterableType
                .get()
                .isReferenceType() || (
                    refType.isPresent() && refType.get().getClassName().equals("String")
            )) {

            var isString = refType.isPresent() && refType.get().getClassName().equals("String");

            var varDecl = AstTagger.parseStatement(varType + " " + varName + ";");

            var indexInit = AstTagger.parseStatement("int index" + numIndexes + " = 0;");

            String getString = isString ? ".get(index" + numIndexes + ")" :
                               "[index" + numIndexes + "]";

            Statement varSet = AstTagger.parseStatement(
                    varName + " = (" + forEachStmt.getIterable() + ")" + getString + ";");

            Expression updateIndex = AstTagger.parseExpression("index" + numIndexes + "++");

            String lengthString = isString ? ".length()" : ".length";
            Expression condition = AstTagger.parseExpression(
                    "index" + numIndexes + " < (" + forEachStmt.getIterable() + ")" + lengthString);


            this.addVarSetToBlock(forEachStmt, varSet);

            this.numIndexes += 1;
            this.buildLoopBlock(nodeBuilder, Optional.of(forEachStmt.getBody()),
                                Optional.of(condition), List.of(varDecl, indexInit),
                                List.of(updateIndex), true, methodNamespace
            );
        } else {
            // We're dealing with an iterable and should represent it with an iterator for loop
            ResolvedTypeParametersMap itTypeParams = iterableType
                    .get()
                    .asReferenceType()
                    .typeParametersMap();

            if (itTypeParams.getTypes().size() != 1) {
                throw new UnsupportedOperationException(
                        "An iterable with more than one type was " + "given: " + itTypeParams);
            }

            var iteratorType = itTypeParams.getTypes().get(0).asReferenceType().getQualifiedName();
            var iteratorInit = AstTagger.parseStatement(
                    "Iterator<" + iteratorType + "> " + "iterator" + numIterators + " = " + forEachStmt.getIterable() + ".iterator();");

            var varDecl = AstTagger.parseStatement(varType + " " + varName + ";");
            var varSet = AstTagger.parseStatement(
                    varName + " = iterator" + numIterators + ".next();");
            var condition = AstTagger.parseExpression("iterator" + numIterators + ".hasNext()");

            this.addVarSetToBlock(forEachStmt, varSet);
            numIterators++;
            this.buildLoopBlock(nodeBuilder, Optional.of(forEachStmt.getBody()),
                                Optional.of(condition), List.of(varDecl, iteratorInit), List.of(),
                                true, methodNamespace
            );
        }
    }

    private void addVarSetToBlock(ForEachStmt forEachStmt, Statement varSet) {
        if (forEachStmt.getBody().isBlockStmt()) {
            forEachStmt.getBody().asBlockStmt().addStatement(0, varSet);
        } else if (forEachStmt.getParentNode().isPresent()) {
            var newBlock = new BlockStmt();
            var body = forEachStmt.getBody();
            body.replace(newBlock);
            newBlock.addStatement(varSet);
            newBlock.addStatement(body);
        } else {
            throw new UnsupportedOperationException(
                    "Attempted to create a tagged AST for a " + "tree with a for each body that " + "has no " + "parent");
        }
    }

    private void buildForStmt(ForStmt forStmt, CfgGenerator.AstNode.Builder nodeBuilder,
                              String[] methodNamespace) {
        this.buildLoopBlock(nodeBuilder, Optional.of(forStmt.getBody()), forStmt.getCompare(),
                            forStmt.getInitialization(), forStmt.getUpdate(), true, methodNamespace
        );
    }

    private void buildWhileStmt(WhileStmt whileStmt, CfgGenerator.AstNode.Builder nodeBuilder,
                                String[] methodNamespace) {
        this.buildLoopBlock(nodeBuilder, Optional.of(whileStmt.getBody()),
                            Optional.of(whileStmt.getCondition()), List.of(), List.of(), true,
                            methodNamespace
        );
    }

    // Decision block handlers
    private void buildDecisionBlock(IfStmt ifStmt, CfgGenerator.AstNode.Builder nodeBuilder,
                                    String[] methodNamespace) {
        var decisionBlockBuilder = nodeBuilder.initContents().initDecisionBlock();

        var blockBuilder = decisionBlockBuilder.initBlock();
        this.buildBlock(Optional.of(ifStmt.getThenStmt()), blockBuilder, methodNamespace);

        var conditionBuilder = decisionBlockBuilder.initCondition();
        this.buildCondition(ifStmt.getCondition(), conditionBuilder);

        if (ifStmt.getElseStmt().isPresent()) {
            var elseBuilder = decisionBlockBuilder.initElse().initSome();
            if (ifStmt.getElseStmt().get().isBlockStmt()) {
                elseBuilder.setBreakable(false);
                elseBuilder.initLambdaFunctions(0);
                elseBuilder.initLabel().setNone(Void.VOID);
                elseBuilder.setNodeType("ElseBlock");
                this.buildBlock(Optional.of(ifStmt.getElseStmt().get().asBlockStmt()),
                                elseBuilder.initContents().initBlock(), methodNamespace
                );
            } else {
                this.buildStatement(ifStmt.getElseStmt().get(), elseBuilder, methodNamespace);
            }
        } else {
            decisionBlockBuilder.initElse().setNone(Void.VOID);
        }
    }

    private final Expression TRUE = new BooleanLiteralExpr(true);

    private Optional<CfgGenerator.DecisionBlock.Builder> buildSwitchCase(SwitchEntry switchEntry,
                                                                         CfgGenerator.AstNode.Builder nodeBuilder, String varName, boolean last, String[] methodNamespace) {
        nodeBuilder.setNodeType("SwitchCase");
        nodeBuilder.initLabel().setNone(Void.VOID);
        nodeBuilder.setBreakable(true);
        if (switchEntry.getLabels().isEmpty() && last) {
            this.buildSwitchBlock(switchEntry.getStatements(),
                                  nodeBuilder.initContents().initBlock(), methodNamespace
            );
            return Optional.empty();
        }

        var decisionBuilder = nodeBuilder.initContents().initDecisionBlock();
        var conditionBuilder = decisionBuilder.initCondition();
        var condStringBuilder = new StringBuilder();
        if (!switchEntry.getLabels().isEmpty()) {
            var firstEntry = switchEntry.getLabels().getFirst().orElseThrow();
            condStringBuilder
                    .append(varName)
                    .append(" == ")
                    .append(this.canonicalizer.canonicalize(firstEntry));
            for (var expr : switchEntry.getLabels().stream().skip(1).toList()) {
                condStringBuilder
                        .append(" || ")
                        .append(varName)
                        .append(" == ")
                        .append(this.canonicalizer.canonicalize(expr));
            }

        } else {
            condStringBuilder.append(this.canonicalizer.canonicalize(this.TRUE));
        }

        var condition = AstTagger.parseExpression(condStringBuilder.toString());
        this.buildCondition(condition, conditionBuilder);

        this.buildSwitchBlock(
                switchEntry.getStatements(), decisionBuilder.initBlock(), methodNamespace);
        return Optional.of(decisionBuilder);
    }

    private void buildSwitchBlock(NodeList<Statement> statements,
                                  CfgGenerator.Block.Builder blockBuilder,
                                  String[] methodNamespace) {
        var statementsBuilder = blockBuilder.initStatements(statements.size());

        var statementsIterator = statementsBuilder.iterator();
        var statementIterator = statements.iterator();

        while (statementsIterator.hasNext()) {
            var statementBuilder = statementsIterator.next();
            var statement = statementIterator.next();

            this.buildStatement(statement, statementBuilder, methodNamespace);
        }
    }

    private void buildDecisionBlock(SwitchStmt switchStmt,
                                    CfgGenerator.AstNode.Builder nodeBuilder,
                                    String[] methodNamespace) {
        var node = nodeBuilder;

        var varName = switchStmt.getSelector().toString();

        int size = switchStmt.getEntries().size();
        int current = 0;
        for (var entry : switchStmt.getEntries()) {
            var last = current >= size - 1;
            var decBuilder = this.buildSwitchCase(entry, node, varName, last, methodNamespace);

            current++;
            if (current < size && decBuilder.isPresent()) {
                node = decBuilder.get().initElse().initSome();
            } else if (decBuilder.isPresent()) {
                decBuilder.get().initElse().setNone(Void.VOID);
            } else if (!last) {
                throw new RuntimeException(
                        "There was an error while attempting to process a " + "switch statement " + "where a decision builder was not " + "produced for the switch entry " + entry + " even " + "though it was not the last entry in the statement");
            }
        }
    }

    // Exception flow statement handlers
    private void buildTryBlock(TryStmt tryStmt, CfgGenerator.AstNode.Builder nodeBuilder,
                               String[] methodNamespace) {
        var tryBuilder = nodeBuilder.initContents().initTryBlock();
        this.buildBlock(Optional.of(tryStmt.getTryBlock()), tryBuilder.initBlock(),
                        methodNamespace
        );

        if (tryStmt.getFinallyBlock().isPresent()) {
            this.buildBlock(Optional.of(tryStmt.getFinallyBlock().get()),
                            tryBuilder.initFinally().initSome(), methodNamespace
            );
        } else {
            tryBuilder.initFinally().setNone(Void.VOID);
        }

        var catchBlocksBuilder = tryBuilder.initCatches(tryStmt.getCatchClauses().size());
        var catchBlocksIterator = catchBlocksBuilder.iterator();

        for (var catchClause : tryStmt.getCatchClauses()) {
            this.buildCatchBlock(catchClause, catchBlocksIterator.next(), methodNamespace);
        }
    }

    private void buildCatchBlock(CatchClause catchClause,
                                 CfgGenerator.CatchBlock.Builder catchBuilder,
                                 String[] methodNamespace) {

        List<ReferenceType> catchTypes;
        if (catchClause.getParameter().getType().isUnionType()) {
            catchTypes = catchClause.getParameter().getType().asUnionType().getElements();
        } else {
            catchTypes = List.of(catchClause.getParameter().getType().asReferenceType());
        }

        var allTypes = catchTypes.stream().flatMap(rt -> {
            try {
                var resolvedType = rt.resolve();
                List<ResolvedReferenceType> ancestors = resolvedType
                        .asReferenceType()
                        .getAllClassesAncestors();
                List<String> result = new ArrayList<>();
                result.add(resolvedType.describe());
                for (var ancestor : ancestors) {
                    result.add(ancestor.describe());
                }
                return result.stream();
            } catch (UnsolvedSymbolException ignored) {
                return Stream.of(rt.toString());
            }
        }).toList();

        var exceptionTypesBuilder = catchBuilder.initExceptionTypes(allTypes.size());
        int i = 0;
        for (var type : allTypes) {
            exceptionTypesBuilder.set(i, new Text.Reader(type));
            i++;
        }

        this.buildBlock(Optional.of(catchClause.getBody()), catchBuilder.initBlock(),
                        methodNamespace
        );
    }

    private void buildThrowStmt(ThrowStmt throwStmt, CfgGenerator.AstNode.Builder nodeBuilder) {
        var throwBuilder = nodeBuilder.initContents().initThrowStatement();
        throwBuilder.setStatement(throwStmt.toString());

        try {
            var exprType = throwStmt.getExpression().calculateResolvedType();
            var ancestors = exprType.asReferenceType().getAllClassesAncestors();
            List<String> types = new ArrayList<>(ancestors.size() + 1);
            types.add(exprType.describe());
            for (var ancestorType : ancestors) {
                types.add(ancestorType.describe());
            }

            var exceptionsBuilder = throwBuilder.initException(types.size());
            int i = 0;
            for (var type : types) {
                exceptionsBuilder.set(i, new Text.Reader(type));
                i++;
            }
        } catch (Exception ignored) {
            var exceptionsBuilder = throwBuilder.initException(1);
            exceptionsBuilder.set(0, new Text.Reader(throwStmt.getExpression().toString()));
        }
    }

    // Control flow statement handlers
    private void buildBreakStmt(BreakStmt breakStmt, CfgGenerator.AstNode.Builder nodeBuilder) {
        var breakBuilder = nodeBuilder.initContents().initBreakStatement();
        if (breakStmt.getLabel().isPresent()) {
            breakBuilder.initLabel().setSome(breakStmt.getLabel().get().toString());
        } else {
            breakBuilder.initLabel().setNone(Void.VOID);
        }
    }

    private void buildContinueStmt(ContinueStmt continueStmt,
                                   CfgGenerator.AstNode.Builder nodeBuilder) {
        var continueBuilder = nodeBuilder.initContents().initContinueStatement();
        if (continueStmt.getLabel().isPresent()) {
            continueBuilder.initLabel().setSome(continueStmt.getLabel().get().toString());
        } else {
            continueBuilder.initLabel().setNone(Void.VOID);
        }
    }

    private void buildReturnStmt(ReturnStmt returnStmt, CfgGenerator.AstNode.Builder nodeBuilder) {
        var returnBuilder = nodeBuilder.initContents().initReturnStatement();
        if (returnStmt.getExpression().isPresent()) {
            returnBuilder
                    .initExpression()
                    .setSome(this.canonicalizer.canonicalize(returnStmt.getExpression().get()));
        } else {
            returnBuilder.initExpression().setNone(Void.VOID);
        }
    }

    // Helper build methods

    /**
     * Chooses the appropriate build method to build an AST Node for the current statement
     *
     * @param statement        The statement that you want to build an AST Node for
     * @param statementBuilder The AST Node builder to handle building this AST Node
     * @param methodNamespace  The method namespace of the current statement
     */
    private void buildStatement(Statement statement,
                                CfgGenerator.AstNode.Builder statementBuilder,
                                String[] methodNamespace) {
        if (statement.isLabeledStmt()) {
            statementBuilder.initLabel().setSome(statement.asLabeledStmt().getLabel().toString());
            statement = statement.asLabeledStmt().getStatement();
        } else {
            statementBuilder.initLabel().setNone(Void.VOID);
        }

        statementBuilder.setBreakable(false);
        statementBuilder.setNodeType(statement.getClass().getSimpleName());

        List<LambdaExpr> lambdaExprs = statement.findAll(LambdaExpr.class);
        var lambdasBuilderIt = statementBuilder.initLambdaFunctions(lambdaExprs.size()).iterator();
        for (var lambdaExpr : lambdaExprs) {
            this.buildAnonymousFunction(lambdaExpr, lambdasBuilderIt.next(), methodNamespace);
        }
        switch (statement) {

            // Passed to the correct handler
            case DoStmt doStmt -> this.buildDoStmt(doStmt, statementBuilder, methodNamespace);
            case ForEachStmt forEachStmt ->
                    this.buildForEachStmt(forEachStmt, statementBuilder, methodNamespace);
            case ForStmt forStmt -> this.buildForStmt(forStmt, statementBuilder, methodNamespace);
            case WhileStmt whileStmt ->
                    this.buildWhileStmt(whileStmt, statementBuilder, methodNamespace);
            case IfStmt ifStmt ->
                    this.buildDecisionBlock(ifStmt, statementBuilder, methodNamespace);
            case SwitchStmt switchStmt ->
                    this.buildDecisionBlock(switchStmt, statementBuilder, methodNamespace);
            case TryStmt tryStmt -> this.buildTryBlock(tryStmt, statementBuilder, methodNamespace);
            case ThrowStmt throwStmt -> this.buildThrowStmt(throwStmt, statementBuilder);
            case BreakStmt breakStmt -> this.buildBreakStmt(breakStmt, statementBuilder);
            case ContinueStmt continueStmt ->
                    this.buildContinueStmt(continueStmt, statementBuilder);
            case ReturnStmt returnStmt -> this.buildReturnStmt(returnStmt, statementBuilder);

            // Expected to be handled elsewhere
            case BlockStmt ignored -> throw new UnsupportedOperationException(
                    "Block statement " + "being handled " + "in" + " build statement" + " instead"
                    + " of " + "build block as " + "it " + "should be.");
            // Handled here
            default -> {
                var stmtBuilder = statementBuilder.initContents().initStatement();
                stmtBuilder.setCode(this.canonicalizer.canonicalize(statement));
            }
        }
    }

    /**
     * Handles building possible AST blocks into lists of AST Nodes, if they are only single nodes
     * then it treats them as a single node block
     *
     * @param block           The optional block that you want to generate a list of AST Nodes for
     * @param blockBuilder    The block builder that you will populate with AST Nodes
     * @param methodNamespace The method namespace of the current statement
     */
    private void buildBlock(Optional<? extends Statement> block,
                            CfgGenerator.Block.Builder blockBuilder, String[] methodNamespace) {
        if (block.isPresent() && block.get().isBlockStmt()) {
            Stack<Statement> statements = new Stack<>();
            List<Statement> statementList = new ArrayList<>();
            statements.addAll(block.get().asBlockStmt().getStatements());
            while (!statements.isEmpty()) {
                var statement = statements.pop();
                if (statement.isBlockStmt()) {
                    statements.addAll(statement.asBlockStmt().getStatements());
                } else {
                    statementList.add(statement);
                }
            }

            var statementsBuilder = blockBuilder.initStatements(statementList.size());

            Collections.reverse(statementList);
            var statementsBuilderIterator = statementsBuilder.iterator();
            var statementsIterator = statementList.iterator();

            while (statementsBuilderIterator.hasNext() && statementsIterator.hasNext()) {
                var statementBuilder = statementsBuilderIterator.next();
                var statement = statementsIterator.next();
                this.buildStatement(statement, statementBuilder, methodNamespace);
            }
        } else if (block.isEmpty()) {
            blockBuilder.initStatements(0);
        } else {
            var statementsBuilder = blockBuilder.initStatements(1);
            this.buildStatement(block.get(), statementsBuilder.get(0), methodNamespace);
        }
    }

    /**
     * Build a condition tree node
     *
     * @param condition        the expression that makes up the condition
     * @param conditionBuilder the builder to handle building the condition
     */
    private void buildCondition(Expression condition,
                                CfgGenerator.Condition.Builder conditionBuilder) {
        if (condition.isBinaryExpr()) {
            BinaryExpr binaryExpr = condition.asBinaryExpr();
            if (binaryExpr.getOperator() == BinaryExpr.Operator.AND) {
                this.buildAndCondition(binaryExpr.getLeft(), binaryExpr.getRight(),
                                       conditionBuilder.initAnd()
                );
                return;
            } else if (binaryExpr.getOperator() == BinaryExpr.Operator.OR) {
                this.buildOrCondition(binaryExpr.getLeft(), binaryExpr.getRight(),
                                      conditionBuilder.initOr()
                );
                return;
            }
        }
        conditionBuilder.setUnit(this.canonicalizer.canonicalize(condition));
    }

    /**
     * Handles calling the appropriate builders for the left and right expression
     *
     * @param left       The left side of the and condition
     * @param right      The right side of the and condition
     * @param andBuilder The builder for the and condition
     */
    private void buildAndCondition(Expression left, Expression right,
                                   CfgGenerator.Condition.And.Builder andBuilder) {
        this.buildCondition(left, andBuilder.initLeft());
        this.buildCondition(right, andBuilder.initRight());
    }

    /**
     * Handles calling the appropriate builders for the left and right expression
     *
     * @param left      The left side of the or condition
     * @param right     The right side of the or condition
     * @param orBuilder The builder for the or condition
     */
    private void buildOrCondition(Expression left, Expression right,
                                  CfgGenerator.Condition.Or.Builder orBuilder) {
        this.buildCondition(left, orBuilder.initLeft());
        this.buildCondition(right, orBuilder.initRight());
    }

    private void buildTextList(List<? extends Node> nodes, TextList.Builder builder) {
        int i = 0;
        for (Node node : nodes) {
            Text.Reader reader;
            if (node instanceof Expression expression) {
                reader = new Text.Reader(this.canonicalizer.canonicalize(expression));
            } else if (node instanceof Statement statement) {
                reader = new Text.Reader(this.canonicalizer.canonicalize(statement));
            } else {
                i++;
                continue;
            }
            builder.set(i, reader);
            i++;
        }
    }

    private static Expression parseExpression(String expr) {
        try {
            return StaticJavaParser.parseExpression(expr);
        } catch (Exception e) {
            System.err.println("Expression: " + expr);
            throw e;
        }
    }

    private static Statement parseStatement(String expr) {
        try {
            return StaticJavaParser.parseStatement(expr);
        } catch (Exception e) {
            System.err.println("Statement: " + expr);
            throw e;
        }
    }
}
