package edu.ncsu.lab.ast_tagger;

import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.*;
import com.github.javaparser.ast.comments.BlockComment;
import com.github.javaparser.ast.comments.JavadocComment;
import com.github.javaparser.ast.comments.LineComment;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.modules.*;
import com.github.javaparser.ast.nodeTypes.NodeWithName;
import com.github.javaparser.ast.stmt.*;
import com.github.javaparser.ast.type.*;
import edu.ncsu.lab.cfg_gen.api.CfgGenerator;
import org.capnproto.MessageBuilder;
import org.javatuples.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.Stream;

public class AstTagger {

    private static class ReadersIdsPair {
        private Stream<CfgGenerator.AstNode.Reader> readers;
        private Stream<Integer> ids;

        public ReadersIdsPair(Stream<CfgGenerator.AstNode.Reader> readers, Stream<Integer> ids) {
            this.readers = readers;
            this.ids = ids;
        }
    }

    private static class PairCollector implements Collector<ReadersIdsPair,
            PairCollector.ListStreamPair, Pair<List<CfgGenerator.AstNode.Reader>, List<Integer>>> {

        @Override
        public Supplier<ListStreamPair> supplier() {
            return ListStreamPair::new;
        }

        @Override
        public BiConsumer<ListStreamPair, ReadersIdsPair> accumulator() {
            return (lsp, mp) -> {
                lsp.readersList.add(mp.readers);
                lsp.methodIdsList.add(mp.ids);
            };
        }

        @Override
        public BinaryOperator<ListStreamPair> combiner() {
            return (lsp1, lsp2) -> {
                
            };
        }

        @Override
        public Function<ListStreamPair, Pair<List<CfgGenerator.AstNode.Reader>, List<Integer>>> finisher() {
            return null;
        }

        @Override
        public Set<Characteristics> characteristics() {
            return null;
        }

        private static class ListStreamPair {
            private List<Stream<CfgGenerator.AstNode.Reader>> readersList = new ArrayList<>();
            private List<Stream<Integer>> methodIdsList = new ArrayList<>();
        }
    }

    public CfgGenerator.Message.Reader visit(CompilationUnit compilationUnit, String programId) {
        String baseNamespace = compilationUnit.getPackageDeclaration()
                                              .map(NodeWithName::getNameAsString)
                                              .orElse("");

        org.capnproto.MessageBuilder capnpMessageBuilder = new MessageBuilder();

        // Create a message builder to populate with nodes
        var cfgMessageBuilder = capnpMessageBuilder.initRoot(CfgGenerator.Message.factory);

        // Set the program ID on the message equal to the passed in program ID
        cfgMessageBuilder.setProgramId(programId);
        cfgMessageBuilder.getNodes()
                         .get(0);

        var cfgMessageData = compilationUnit.getTypes()
                                            .stream()
                                            .map(td -> {
                                                if (td.isClassOrInterfaceDeclaration()) {
                                                    var tdData =
                                                            this.visit(td.asClassOrInterfaceDeclaration(), baseNamespace);
                                                }
                                            });
        for (var typeDeclaration : compilationUnit.getTypes()) {
            if (typeDeclaration.isClassOrInterfaceDeclaration()) {
                this.visit(typeDeclaration.asClassOrInterfaceDeclaration(), baseNamespace);
            }
        }
        return cfgMessageBuilder.asReader();
    }

    public List<Long> visit(TypeParameter n, Void arg) {
        return null;
    }

    public List<Long> visit(LineComment n, Void arg) {
        return null;
    }

    public List<Long> visit(BlockComment n, Void arg) {
        return null;
    }

    public Pair<List<CfgGenerator.AstNode.Reader>, List<Integer>> visit(ClassOrInterfaceDeclaration declaration, String namespace) {

        return null;
    }

    public List<Long> visit(RecordDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(CompactConstructorDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(EnumDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(EnumConstantDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(AnnotationDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(AnnotationMemberDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(FieldDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(VariableDeclarator n, Void arg) {
        return null;
    }

    public List<Long> visit(ConstructorDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(MethodDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(Parameter n, Void arg) {
        return null;
    }

    public List<Long> visit(InitializerDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(JavadocComment n, Void arg) {
        return null;
    }

    public List<Long> visit(ClassOrInterfaceType n, Void arg) {
        return null;
    }

    public List<Long> visit(PrimitiveType n, Void arg) {
        return null;
    }

    public List<Long> visit(ArrayType n, Void arg) {
        return null;
    }

    public List<Long> visit(ArrayCreationLevel n, Void arg) {
        return null;
    }

    public List<Long> visit(IntersectionType n, Void arg) {
        return null;
    }

    public List<Long> visit(UnionType n, Void arg) {
        return null;
    }

    public List<Long> visit(VoidType n, Void arg) {
        return null;
    }

    public List<Long> visit(WildcardType n, Void arg) {
        return null;
    }

    public List<Long> visit(UnknownType n, Void arg) {
        return null;
    }

    public List<Long> visit(ArrayAccessExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(ArrayCreationExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(ArrayInitializerExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(AssignExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(BinaryExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(CastExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(ClassExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(ConditionalExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(EnclosedExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(FieldAccessExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(InstanceOfExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(StringLiteralExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(IntegerLiteralExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(LongLiteralExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(CharLiteralExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(DoubleLiteralExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(BooleanLiteralExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(NullLiteralExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(MethodCallExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(NameExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(ObjectCreationExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(ThisExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(SuperExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(UnaryExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(VariableDeclarationExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(MarkerAnnotationExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(SingleMemberAnnotationExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(NormalAnnotationExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(MemberValuePair n, Void arg) {
        return null;
    }

    public List<Long> visit(ExplicitConstructorInvocationStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(LocalClassDeclarationStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(LocalRecordDeclarationStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(AssertStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(BlockStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(LabeledStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(EmptyStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(ExpressionStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(SwitchStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(SwitchEntry n, Void arg) {
        return null;
    }

    public List<Long> visit(BreakStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(ReturnStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(IfStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(WhileStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(ContinueStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(DoStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(ForEachStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(ForStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(ThrowStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(SynchronizedStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(TryStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(CatchClause n, Void arg) {
        return null;
    }

    public List<Long> visit(LambdaExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(MethodReferenceExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(TypeExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(NodeList n, Void arg) {
        return null;
    }

    public List<Long> visit(Name n, Void arg) {
        return null;
    }

    public List<Long> visit(SimpleName n, Void arg) {
        return null;
    }

    public List<Long> visit(ImportDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(ModuleDeclaration n, Void arg) {
        return null;
    }

    public List<Long> visit(ModuleRequiresDirective n, Void arg) {
        return null;
    }

    public List<Long> visit(ModuleExportsDirective n, Void arg) {
        return null;
    }

    public List<Long> visit(ModuleProvidesDirective n, Void arg) {
        return null;
    }

    public List<Long> visit(ModuleUsesDirective n, Void arg) {
        return null;
    }

    public List<Long> visit(ModuleOpensDirective n, Void arg) {
        return null;
    }

    public List<Long> visit(UnparsableStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(ReceiverParameter n, Void arg) {
        return null;
    }

    public List<Long> visit(VarType n, Void arg) {
        return null;
    }

    public List<Long> visit(Modifier n, Void arg) {
        return null;
    }

    public List<Long> visit(SwitchExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(YieldStmt n, Void arg) {
        return null;
    }

    public List<Long> visit(TextBlockLiteralExpr n, Void arg) {
        return null;
    }

    public List<Long> visit(PatternExpr n, Void arg) {
        return null;
    }
}
