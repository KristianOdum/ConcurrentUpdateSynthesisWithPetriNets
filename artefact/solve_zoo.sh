#!/bin/bash

results_path=data/zoo_results

./run_verifypn.sh "data/zoo_pn" $results_path
./run_netsynth.sh "data/zoo_ltl" $results_path

