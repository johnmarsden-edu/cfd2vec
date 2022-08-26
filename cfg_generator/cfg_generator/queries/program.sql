--: Program()

--! programs : Program
SELECT id, program_id FROM programs;

--! insert_program : Program
INSERT INTO programs (program_id) VALUES (:program_id) RETURNING *;