#!/bin/bash

data_path=data/synthethic_ltl
results_path=data/synthethic_results
bin="netsynth"

for i in $(ls $data_path ) ; do
	echo "Running $i.."
	t=${i%.ltl}
	$EXECUTOR ./executor.sh "engines/$bin solve $data_path/$t.ltl"  "$results_path/$t.$bin"
done

