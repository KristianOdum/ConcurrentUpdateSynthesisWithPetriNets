#!/bin/bash
./do_plot.sh "Synthetic Networks - Disjoint" "0.1" "1000" ../disjoint.csv
./do_plot.sh "Synthetic Networks - Shared, 1 Waypoint" "0.1" "1000" ../shared_single.csv
./do_plot.sh "Synthetic Networks - Dependent, 1 Waypoint" "0.1" "1000" ../dependent_single.csv
./do_plot.sh "Synthetic Networks - Shared, n/5 Waypoints" "0.1" "1000" ../shared_5.csv
./do_plot.sh "Synthetic Networks - Dependent, n/5 Waypoints" "0.1" "1000" ../dependent_5.csv
./do_plot.sh "Synthetic Networks - Shared, n/10 Waypoints" "0.01" "1000" ../shared_10.csv
./do_plot.sh "Synthetic Networks - Dependent, n/10 Waypoints" "0.1" "1000" ../dependent_10.csv

./do_plot.sh "Synthetic Networks - Nested Zoo" "0.01" "100" ../nested.csv "1036"
./do_plot.sh "Synthetic Networks - Zoo" "0.01" "100" ../zoo.csv 

