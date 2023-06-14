cd .. || exit;

cd node_gen_java || exit;
c_targets='../data/F19_All/Test/Data/CodeStates/CodeStates.csv,../data/F19_All/Train/Data/CodeStates/CodeStates.csv,../data/S19_All/Data/CodeStates/CodeStates.csv';
p_targets=$(printf '%s,' ../data/Data/project*.csv | rev | cut -c2- | rev);
./gradlew app:run --args="calculate -c ${c_targets} -p ${p_targets}"
cd .. || exit;

cd scripts || exit;
