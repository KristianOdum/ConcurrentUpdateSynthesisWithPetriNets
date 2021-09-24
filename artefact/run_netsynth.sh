#!/bin/bash
data_path="$1"
results_path="$2"

if [ -z "$data_path" ] ; then
	echo "No data path";
	exit
fi

if [ -z "$results_path" ] ; then
	echo "No result path"
	exit
fi

bin=netsynth
for raw in $data_path/*.ltl
do
	echo "Running $raw"
	b=$(basename $raw)
	file=${b%.ltl}
    $EXECUTOR ./executor.sh "engines/$bin solve $data_path/$file.ltl" "$results_path/${file}.$bin"
done
