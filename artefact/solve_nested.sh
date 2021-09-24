#!/bin/bash

results_path="data/nested_results/"

./run_verifypn.sh "data/nested_pn" "$results_path" 
./run_netsynth.sh "data/nested_ltl" "$results_path" 

