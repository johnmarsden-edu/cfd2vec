package edu.ncsu.edm.graphgenerator;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import com.github.javaparser.ast.ArrayCreationLevel;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.NodeList;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.*;

public class FlowNode {
    private final Optional<Node> node;
    private final Optional<String> label;
    private final Optional<String> name;
    private final Map<String, String> metadata;

    public FlowNode(Node optNode) {
        this(optNode, null);
    }

    public FlowNode(Node optNode, String label) {
        this.node = Optional.of(optNode);
        this.name = Optional.empty();
        this.label = Optional.ofNullable(label);
        this.metadata = new HashMap<>();
    }

    public FlowNode(String name) {
        this.node = Optional.empty();
        this.name = Optional.of(name);
        this.label = Optional.empty();
        this.metadata = new HashMap<>();
    }

    public Optional<Node> getNode() {
        return this.node;
    }

    public Optional<String> getName() {
        return this.name;
    }

    public void addMetadata(String key, String value) {
        this.metadata.put(key, value);
    }

    public String getMetadata(String key) {
        return this.metadata.get(key);
    }

    private static long blockNum = 0;

    private static StringBuilder addExpressionToStringBuilder(StringBuilder sb, Expression expr) {
        switch (expr) {
            case AnnotationExpr ignored -> sb.append("not supported");
            case LambdaExpr ignored1 -> sb.append("not supported");
            case MethodReferenceExpr mr -> sb.append("not supported");
            case TypeExpr t -> sb.append("not supported");
            case LiteralStringValueExpr ls -> sb.append(ls.getValue());
            case NullLiteralExpr nl -> sb.append("null");
            case ArrayAccessExpr aa -> {
                sb.append("access array ");
                addExpressionToStringBuilder(sb, aa.getName());
                sb.append(" at ");
                addExpressionToStringBuilder(sb, aa.getIndex());
            }
            case ArrayCreationExpr ac -> {
                sb.append("create array of type ").append(ac.getElementType().asString());
                for (ArrayCreationLevel level: ac.getLevels()) {
                    sb.append('[');
                    if (level.getDimension().isPresent()) {
                        addExpressionToStringBuilder(sb, level.getDimension().get());
                    }
                    sb.append(']');
                }
                if (ac.getInitializer().isPresent()) {
                    sb.append(" and ");
                    addExpressionToStringBuilder(sb, ac.getInitializer().get());
                }
            }
            case ArrayInitializerExpr ai -> {
                sb.append("initialize with {");
                addExpressionsToStringBuilder(sb, ai.getValues());
                sb.append("}");
            }
            case AssignExpr a -> {
                sb.append("assign to ");
                addExpressionToStringBuilder(sb, a.getTarget());
                sb.append(" using ").append(a.getOperator()).append(" operator with ");
                addExpressionToStringBuilder(sb, a.getValue());
            }
            case BinaryExpr b -> {
                addExpressionToStringBuilder(sb, b.getLeft());
                sb.append(" ").append(b.getOperator()).append(" ");
                addExpressionToStringBuilder(sb, b.getRight());
            }
            case BooleanLiteralExpr bl -> sb.append(bl.getValue());
            case CastExpr c -> sb.append(c.getExpression().toString());
            case ClassExpr c -> sb.append("access class of ").append(c.getClass().getName());
            case ConditionalExpr c -> {
                sb.append("if ");
                addExpressionToStringBuilder(sb, c.getCondition());
                sb.append(" then ");
                addExpressionToStringBuilder(sb, c.getThenExpr());
                sb.append(" otherwise ");
                addExpressionToStringBuilder(sb, c.getElseExpr());
            }
            case EnclosedExpr e -> {
                sb.append('(');
                addExpressionToStringBuilder(sb, e.getInner());
                sb.append(')');
            }
            case FieldAccessExpr f -> {
                sb.append("access field ").append(f.getNameAsString()).append(" from ");
                addExpressionToStringBuilder(sb, f.getScope());
            }
            case InstanceOfExpr i -> {
                addExpressionToStringBuilder(sb, i.getExpression());
                sb.append(" is an instance of ").append(i.getTypeAsString());
                if (i.getPattern().isPresent()) {
                    sb.append(" with ");
                    addExpressionToStringBuilder(sb, i.getExpression());
                    sb.append(" stored in ").append(i.getName().get()).append(" with the type ").append(i.getTypeAsString());
                }
            }
            case MethodCallExpr m -> {
                sb.append("call ").append(m.getNameAsString());
                if (m.getScope().isPresent()) {
                    sb.append(" on ");
                    addExpressionToStringBuilder(sb, m.getScope().get());
                }
            }
            case NameExpr n -> sb.append(n.getNameAsString());
            case ObjectCreationExpr oc -> sb.append("create object of type ").append(oc.getType().getNameAsString());
            case PatternExpr p -> sb.append("named ").append(p.getNameAsString()).append(" and is of type ").append(p.getTypeAsString());
            case SuperExpr s -> sb.append("called super").append(s.getTypeName().isPresent() ? "on " + s.getTypeName().get().asString() : " locally");
            case SwitchExpr s -> {
                sb.append("switch on ");
                addExpressionToStringBuilder(sb, s.getSelector());
            }
            case ThisExpr t -> sb.append("called super").append(t.getTypeName().isPresent() ? "on " + t.getTypeName().get().asString() : " locally");
            case UnaryExpr u -> {
                sb.append("ran ").append(u.getOperator()).append(" on ");
                addExpressionToStringBuilder(sb, u.getExpression());
            }
            case VariableDeclarationExpr v -> {
                sb.append("declared variable(s) ");
                for (VariableDeclarator d: v.getVariables()) {
                    sb.append(d.getNameAsString());
                    if (d.getInitializer().isPresent()) {
                        sb.append(" = ");
                        addExpressionToStringBuilder(sb, d.getInitializer().get());
                    }
                }
            }
            default -> throw new IllegalStateException("Unknown Expression Type: " + expr.getClass().getName());
        }
        return sb;
    }

    private static StringBuilder addExpressionsToStringBuilder(StringBuilder sb, NodeList<Expression> exprs) {
        Optional<Expression> first = exprs.stream().findFirst();

        first.ifPresent(expression -> addExpressionToStringBuilder(sb, expression));
        exprs.stream().skip(1).forEach(expr -> {
            sb.append(", ");
            addExpressionToStringBuilder(sb, expr);
        });

        return sb;
    }

    private static StringBuilder addStatementToStringBuilder(StringBuilder sb, Statement stmt) {
        switch (stmt) {
            case AssertStmt a -> sb.append(a.getCheck().toString()).append(" : ")
                    .append(a.getMessage().isPresent() ? a.getMessage().get().toString() : "No Message");
            case BlockStmt b -> sb.append("block ").append(b.hashCode());
            case BreakStmt br -> {
                sb.append("break to ");
                if (br.getLabel().isPresent()) {
                    sb.append(br.getLabel().toString());
                } else {
                    Statement parent = NodeUtils.getParentStatement(br);
                    if (parent.getBegin().isPresent()) {
                        sb.append(parent.getBegin().get());
                    } else {
                        addStatementToStringBuilder(sb, parent);
                    }
                }
            }
            case ContinueStmt c -> {
                sb.append("continue to ");
                if (c.getLabel().isPresent()) {
                    sb.append(c.getLabel().toString());
                } else {
                    addStatementToStringBuilder(sb, NodeUtils.getParentStatement(c));
                }
            }
            case DoStmt d -> sb.append("do while ").append(d.getCondition().toString());
            case EmptyStmt e -> sb.append("empty statement");
            case ExplicitConstructorInvocationStmt ex -> sb.append(ex.toString());
            case ExpressionStmt exs -> addExpressionToStringBuilder(sb, exs.getExpression());
            case ForEachStmt fes -> {
                sb.append("for each ");
                addExpressionToStringBuilder(sb, fes.getVariable()).append(" in ");
                addExpressionToStringBuilder(sb, fes.getIterable());
            }
            case ForStmt fs -> {
                sb.append("For ");
                if (fs.getCompare().isPresent())
                    addExpressionToStringBuilder(sb, fs.getCompare().get());
                else {
                    sb.append("empty statement");
                }

                sb.append(" initially with ");
                if (fs.getInitialization().isEmpty()) {
                    sb.append("nothing");
                } else {
                    addExpressionsToStringBuilder(sb, fs.getInitialization());
                }

                sb.append(" do ");
                if (fs.getUpdate().isEmpty()) {
                    sb.append("nothing");
                } else {
                    addExpressionsToStringBuilder(sb, fs.getUpdate());
                }
            }
            case IfStmt i -> {
                sb.append("if ");
                addExpressionToStringBuilder(sb, i.getCondition());
                if (i.getElseStmt().isPresent()) {
                    sb.append(" and else");
                }
            }
            case LabeledStmt l -> sb.append("label ").append(l.getLabel().asString());
            case LocalClassDeclarationStmt lcds -> sb.append("define local class ")
                    .append(lcds.getClassDeclaration().getNameAsString());
            case LocalRecordDeclarationStmt lrds -> sb.append("define local record ")
                    .append(lrds.getRecordDeclaration().getNameAsString());
            case ReturnStmt r -> {
                sb.append("return");
                if (r.getExpression().isPresent()) {
                    sb.append("s ");
                    addExpressionToStringBuilder(sb, r.getExpression().get());
                }
            }
            case SwitchStmt s -> {
                sb.append("switch on ");
                addExpressionToStringBuilder(sb, s.getSelector());
            }
            case SynchronizedStmt sync -> {
                sb.append("sync and lock on ");
                addExpressionToStringBuilder(sb, sync.getExpression());
            }
            case ThrowStmt thr -> {
                sb.append("throws ");
                addExpressionToStringBuilder(sb, thr.getExpression());
            }
            case TryStmt t -> sb.append("try");
            case WhileStmt w -> {
                sb.append("while ");
                addExpressionToStringBuilder(sb, w.getCondition());
            }
            case YieldStmt y -> {
                sb.append("yield ");
                addExpressionToStringBuilder(sb, y.getExpression());
            }
            default -> throw new IllegalArgumentException("Unrecognized Statement Type: " + stmt.getClass().getName()); 
        };

        return sb;
    }

    @Override
    public String toString() {
        if (this.node.isPresent()) {
            Node current = this.node.get();
            StringBuilder sb = new StringBuilder();
            switch (current) {
                case MethodDeclaration methodDeclaration -> sb.append(methodDeclaration.getNameAsString());
                case Statement statement -> {
                    addStatementToStringBuilder(sb, statement);
                }
                case CatchClause c ->  sb.append("Catch ").append(c.getParameter().toString());
                case SwitchEntry s -> {
                    sb.append("Case ");
                    addExpressionsToStringBuilder(sb, s.getLabels());
                }
                case Expression e -> addExpressionToStringBuilder(sb, e);
                default -> throw new IllegalStateException("Did not handle possible type of node: " + current.getClass().getCanonicalName());
            };

            return sb.toString();
        }

        if (this.name.isPresent())
            return this.name.get();
        else
            throw new IllegalStateException("Either Node or Name should be set");
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null || obj.getClass() != this.getClass()) {
            return false;
        }

        final FlowNode other = (FlowNode) obj;
        if (this.node.isPresent() && other.node.isPresent() && this.node.get() == other.node.get()) {
            return true;
        }

        return this.name.isPresent() && other.name.isPresent() && this.name.get().equals(other.name.get());
    }

    @Override
    public int hashCode() {
        if (this.node.isPresent()) {
            return this.node.get().hashCode();
        } else if (this.name.isPresent()) {
            return this.name.get().hashCode();
        } else {
            throw new IllegalStateException("Neither Node nor Name are present");
        }
    }

    public Optional<String> getLabel() {
        return label;
    }
}
