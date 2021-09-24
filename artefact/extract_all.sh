#!/bin/bash

B="netsynth verifypn.269"
./extract.sh ./data/synthethic_results/ "Disjoint" '^$' '^$' $B > disjoint.csv
./extract.sh ./data/synthethic_results/ "Dependent_5_" '^$' '^$' $B > dependent_5.csv
./extract.sh ./data/synthethic_results/ "Shared_5_" '^$' '^$' $B > shared_5.csv
./extract.sh ./data/synthethic_results/ "Dependent_10_" '^$' '^$' $B > dependent_10.csv
./extract.sh ./data/synthethic_results/ "Shared_10_" '^$' '^$' $B > shared_10.csv
./extract.sh ./data/synthethic_results/ "Dependent" 'Dependent_10_' 'Dependent_5_' $B > dependent_single.csv
./extract.sh ./data/synthethic_results/ "Shared" 'Shared_10_' 'Shared_5_' $B > shared_single.csv


./extract.sh ./data/zoo_results/ '.' '^$' '^$' $B > zoo.csv
./extract.sh ./data/nested_results/ '.' '^$' '^$' $B > nested.csv
