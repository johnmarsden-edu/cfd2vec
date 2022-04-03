package edu.ncsu.edm.graphgenerator;

import java.util.Optional;

import org.jgrapht.graph.DefaultEdge;

public class FlowEdge extends DefaultEdge {
    private final Optional<Boolean> flowCondition;

    public FlowEdge() {
        this.flowCondition = Optional.empty();
    }

    public FlowEdge(boolean flowCondition) {
        this.flowCondition = Optional.of(flowCondition);
    }

    public Optional<Boolean> getFlowCondition() {
        return flowCondition;
    }

    @Override
    public String toString() {
        return this.flowCondition.map(Object::toString).orElse("");
    }
}
