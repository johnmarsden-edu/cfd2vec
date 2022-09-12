cd ..;

cd cfg_generator/cfg_generator || exit;
cargo build --release --bin cfg_generator;
wait;
cargo run --release --bin cfg_generator -- serve false &
cfg_gen_pid=$!;
cd ../..;
echo "CFG Generator PID: $cfg_gen_pid";

cd node_gen_java || exit;
./gradlew app:run --args="generate ../data/F19_All/Test ../data/F19_All/Train ../data/S19_All/" &
node_gen_pid=$!;
cd ..;
echo "Node Generator (Java) PID: $node_gen_pid";

wait "${node_gen_pid}";
kill -TERM "${cfg_gen_pid}";

wait "${cfg_gen_pid}";
cd scripts;