#!/bin/bash
bin="verifypn.269"
data_path=data/synthethic_pn
results_path=data/synthethic_results

for i in $(ls $data_path | grep pnml ) ; do
	n=$(echo "$i" | grep -oP "[0-9]+(?=\.pnml)")
	echo "Running $i.."
	t=${i%.pnml}
	C="engines/$bin -p -r 0 -q 0 --strategy-output /dev/null $data_path/$t.pnml $data_path/$t.q"
	$EXECUTOR ./executor.sh "$C"  "$results_path/$t.$bin"
done

