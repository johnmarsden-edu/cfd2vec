--: Edge (direction?, exception?)

--! program_edges : Edge
SELECT id, graph_id, source, target, edge_type, direction, exception
FROM edges
WHERE graph_id IN (
    SELECT id
    FROM graphs
    WHERE graphs.program_id = :program_id
);

--! method_edges : Edge
SELECT id, graph_id, source, target, edge_type, direction, exception
FROM edges
WHERE graph_id = :method_id;

--! node_incoming_edges : Edge
SELECT id, graph_id, source, target, edge_type, direction, exception
FROM edges
WHERE target = :target_id;

--! node_outgoing_edges : Edge
SELECT id, graph_id, source, target, edge_type, direction, exception
FROM edges
WHERE source = :source_id;

--! insert_edge (direction?, exception?): Edge
INSERT INTO edges
    (graph_id, source, target, edge_type, direction, exception)
VALUES
    (:graph_id, :source, :target, :edge_type, :direction, :exception)
RETURNING *;