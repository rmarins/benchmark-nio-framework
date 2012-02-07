#!/usr/bin/gnuplot
set terminal png
set output "memory.png"
set title "memory usage"
set xlabel "time"
set ylabel "size(M Bytes)"
set xdata time
set timefmt "%H:%M:%S"
set format x "%H:%M"
plot "file_dstat" using 1:($2)/1000 title "used" with lines,
"file_dstat" using 1:($3)/1000 title "used" with lines,
"file_dstat" using 1:($4)/1000 title "used" with lines,
"file_dstat" using 1:($5)/1000 title "used" with lines

