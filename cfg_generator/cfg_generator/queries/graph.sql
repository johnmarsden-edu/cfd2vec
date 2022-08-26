--: Graph ()

--! program_graphs : Graph
SELECT id, graph_id, program_id
FROM graphs
WHERE program_id = :program_id;

--! all_graphs : Graph
SELECT id, graph_id, program_id
FROM graphs;

--! insert_graph : Graph
INSERT INTO graphs (graph_id, program_id) VALUES (:graph_id, :program_id) RETURNING *;