#!/bin/bash
data_path="$1"
results_path="$2"

bin="verifypn.269"
echo "$data_path $results_path"

if [ -z "$data_path" ] ; then
	echo "No data path set"
	exit
fi

if [ -z "$results_path" ] ; then
	echo "No result path set"
	exit
fi

for raw in $data_path/*.pnml
do
	echo "Running $raw"
	b=$(basename $raw)
	file=${b%.pnml}
	$EXECUTOR ./executor.sh "engines/$bin -q 0 -r 0 -p --strategy-output /dev/null $data_path/$file.pnml $data_path/$file.q" "$results_path/${file}.$bin"
done

