#!/bin/bash
#SBATCH --mail-type=FAIL
#SBATCH --mem=26000
#SBATCH --time=1:00:00
#SBATCH --error=/dev/null
#SBATCH --output=/dev/null

export LD_LIBRARY_PATH=$LD_LIBRARY_PATH:$(pwd)/engines

if [ -z "$MEMORY" ] ; then
    MEMORY=$((25*1024*1024))
fi

if [ -z "$TIME" ] ; then
    TIME="3600"
fi

ulimit -v $MEMORY

echo "Running\n \"$1\" &> $2"
START=$(date +%s%N | cut -b1-13)
R=$(timeout $TIME /usr/bin/time -f "@@@M%M@@@" $1 2>&1 )
END=$(date +%s%N | cut -b1-13)
echo "$R" > $2
T=$(echo "$END-$START" | bc -l)
echo "@@@T$T@@@" >> $2
echo "Done!"

