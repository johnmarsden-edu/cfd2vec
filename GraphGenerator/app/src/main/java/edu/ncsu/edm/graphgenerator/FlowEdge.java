package edu.ncsu.edm.graphgenerator;

import java.util.Optional;

import com.github.javaparser.ast.expr.Expression;
import org.jgrapht.graph.DefaultEdge;

public class FlowEdge extends DefaultEdge {
    Optional<Expression> flowConditionExpr;

    public FlowEdge() {
        this.flowConditionExpr = Optional.empty();
    }

    public FlowEdge(Expression flowConditionalExpr) {
        this.flowConditionExpr = Optional.of(flowConditionalExpr);
    }

    public Optional<Expression> getFlowConditionExpr() {
        return this.flowConditionExpr;
    }

    @Override
    public String toString() {
        if (this.flowConditionExpr.isPresent()) {
            return this.flowConditionExpr.get().toString();
        }
        return "";
    }
}
