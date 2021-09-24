./do_mem_plot.sh "Synthetic Networks - Disjoint" "1" "25000" ../disjoint.csv
./do_mem_plot.sh "Synthetic Networks - Shared, 1 waypoint" "1" "25000" ../shared_single.csv
./do_mem_plot.sh "Synthetic Networks - Dependent, 1 waypoint" "1" "25000" ../dependent_single.csv
./do_mem_plot.sh "Synthetic Networks - Shared, 5/n waypoints" "1" "25000" ../shared_5.csv
./do_mem_plot.sh "Synthetic Networks - Dependent, 5/n waypoints" "1" "25000" ../dependent_5.csv
./do_mem_plot.sh "Synthetic Networks - Shared, 10/n waypoints" "1" "25000" ../shared_10.csv
./do_mem_plot.sh "Synthetic Networks - Dependent, 10/n waypoints" "1" "25000" ../dependent_10.csv

./do_mem_plot.sh "Synthetic Networks - Nested Zoo" "1" "2500" ../nested.csv "1036"
./do_mem_plot.sh "Synthetic Networks - Zoo" "1" "25000" ../zoo.csv 

