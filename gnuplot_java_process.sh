#!/usr/bin/gnuplot
set terminal png
set output "nb_proc_java.png"
set title "nb proc java"
set xlabel "time"
set ylabel "nb"
set xdata time
set timefmt "%H:%M:%S"
set format x "%H:%M"
plot "nb_java_proc" using 1:2 title "nb proc" with lines
