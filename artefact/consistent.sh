#!/bin/bash
grep "finished synthesizing update" -r data/nested_results/ | grep -oP ".*(?=netsynth)" | sort | uniq > netsynth.sat
grep "no correct update" -r data/nested_results/ | grep -oP ".*(?=netsynth)" | sort | uniq > netsynth.not

grep "Query is sat" -r data/nested_results/  | grep -oP ".*(?<=\:)" | grep -oP ".*(?=verifypn)" | sort | uniq > pn.sat
grep "Query is NOT" -r data/nested_results/  | grep -oP ".*(?<=\:)" | grep -oP ".*(?=verifypn)"  | sort | uniq > pn.not

echo "## Inconsistency in satisfiable problems (if no lines are printed below, the results are consistent) ##"
comm -12 netsynth.sat pn.not

echo "## Inconsistency in unsatisfiable problems (if no lines are printed below, the results are consistent) ##"
comm -12 netsynth.not pn.sat


