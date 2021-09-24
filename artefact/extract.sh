#!/bin/bash
printf "#test"
for b in ${@:5} ; do 
	printf ",$b-time,$b-mem"
done

printf '\n'

for f in $(ls $1 | grep -oP ".+(?=\.)" | grep -v "verifypn" | sort | uniq | grep -P "$2" | grep -v "$3" | grep -v "$4" ) ; do 
printf "$f"
for b in ${@:5} ; do 
	R="3600000,120000000"
	ok=""
	if [ "$b" == "netsynth" ] ; then
		ok=$(grep "finished synthesizing" $1/$f.$b )
	else
		ok=$(grep "Query is" $1/$f.$b )
	fi
	if [ ! -z "$ok" ] ; then
		t=$(grep -oP "(?<=@@@T).*(?=@@@)" $1/$f.$b)
		m=$(grep -oP "(?<=@@@M).*(?=@@@)" $1/$f.$b)
		R="$t,$m"
	fi
	printf ",$R"
done
printf '\n'
done
