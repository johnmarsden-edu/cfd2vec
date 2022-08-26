--: Node (label?, contents?)

--! program_nodes : Node
SELECT id, label, node_type, contents, graph_id
FROM nodes
WHERE graph_id IN (
    SELECT id
    FROM graphs
    WHERE graphs.program_id = :program_id
);

--! method_nodes : Node
SELECT id, label, node_type, contents, graph_id
FROM nodes
WHERE graph_id = :graph_id;

--! insert_node (label?, contents?) : Node
INSERT INTO nodes
    (label, node_type, contents, graph_id)
VALUES (:label, :node_type, :contents, :graph_id)
RETURNING *;