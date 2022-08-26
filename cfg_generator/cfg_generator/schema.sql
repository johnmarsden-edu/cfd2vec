-- Reset Database
DROP TYPE IF EXISTS node_type CASCADE;
DROP TYPE IF EXISTS edge_type CASCADE;
DROP TABLE IF EXISTS programs CASCADE;
DROP TABLE IF EXISTS graphs CASCADE;
DROP TABLE IF EXISTS nodes CASCADE;
DROP TABLE IF EXISTS edges CASCADE;

-- Programs Definition
CREATE TABLE programs (
    id SERIAL PRIMARY KEY,
    program_id VARCHAR NOT NULL
);

-- Methods Definition
CREATE TABLE graphs (
    id SERIAL PRIMARY KEY,
    graph_id VARCHAR,
    program_id INT REFERENCES programs
);

-- Nodes Definition
CREATE TYPE node_type AS ENUM (
    'Source',
    'Sink',
    'Statement',
    'Control',
    'Decision',
    'Exception'
    );

CREATE TABLE nodes (
    id SERIAL PRIMARY KEY,
    label VARCHAR,
    node_type node_type NOT NULL,
    contents VARCHAR,
    graph_id INT REFERENCES graphs
);

-- Edges Definition
CREATE TYPE edge_type AS ENUM (
    'Statement',
    'Decision',
    'Exception'
);

CREATE TABLE edges (
    id SERIAL PRIMARY KEY,
    graph_id INT REFERENCES graphs,
    source INT REFERENCES nodes,
    target INT REFERENCES nodes,
    edge_type edge_type NOT NULL,
    direction BOOLEAN DEFAULT NULL,
    exception VARCHAR DEFAULT NULL
);