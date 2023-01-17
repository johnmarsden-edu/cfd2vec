cd ..;

cd cfg_generator/cfg_generator || exit;
cargo build --release --bin cfg_generator;
wait;
cargo run --release --bin cfg_generator -- serve ../../data/generated_graphs &
cfg_gen_pid=$!;
cd ../..;
echo "CFG Generator PID: $cfg_gen_pid";

cd node_gen_java || exit;
c_targets='../data/F19_All/Test/Data/CodeStates/CodeStates.csv,../data/F19_All/Train/Data/CodeStates/CodeStates.csv,../data/S19_All/Data/CodeStates/CodeStates.csv';
p_targets=$(printf '%s,' ../data/Data/project*.csv | rev | cut -c2- | rev);
./gradlew app:run --args="generate -c ${c_targets} -p ${p_targets}" &
node_gen_pid=$!;
cd ..;
echo "Node Generator (Java) PID: $node_gen_pid";

wait "${node_gen_pid}";
kill -TERM "${cfg_gen_pid}";

wait "${cfg_gen_pid}";
cd scripts;
