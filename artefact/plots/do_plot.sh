#!/bin/bash


B=$(basename $4 )
OUT=${B%.csv}
OUT="cactus-$OUT.pdf"
PLOT="
set xlabel \"Instances\"
set ylabel \"Seconds\"
set logscale y 10
set yrange [$2:$3]
set xrange [:$5]
set terminal pdf enhanced color dashed lw 1  size 5.1,2.7
set output '$OUT'
set key top left

set datafile separator ','
plot '< sort -nk2 -t , $4' using (\$2>=3600000 ? \$2 : (\$2/1000)) with lines dashtype 2 linetype 7 linewidth 3 title \"NetSynth\", \
     '< sort -nk4 -t , $4' using (\$4>=3600000 ? \$4 : (\$4/1000)) with lines dashtype 1 linetype 8 linewidth 3 title \"TAPAAL\""

echo "$PLOT" | gnuplot

