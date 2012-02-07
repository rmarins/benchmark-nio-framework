#!/usr/bin/env python


# see the dstat man page for more details about the options !
# with this command, we're watching the memory and the swap metrics with all
# the values in integer format (without this option, you will have numbers in
# Ko and Mo and it will be hard after to do a graphic with gnuplot ;-)
# the output parameter will produce csv file
# > /dev/null  & is used to run the script in background
dstat -tms --integer --output file > /dev/null &

##
# The monitoring script for the java proceses was simple too:

# this script will print into a file the number of java process name <proc_name>
# every 3 seconds with the time before (the time format is HH:MM:ss
NB_PROCESS_FILE="nb_proc_java"
while true
do
    echo `date "+%T"` "`ps -ef |grep proc_name | wc -l`" >> $NB_PROCESS_FILE
    sleep 3
done

##
# the dstat file was at this format

# "date/time","used","buff","cach","free","used","free"
# 27-11 19:44:09,449216512.0,104566784.0,464535552.0,42463232.0,0.0,0.0
# 27-11 19:44:10,449376256.0,104566784.0,464535552.0,42303488.0,0.0,0.0


##
# After a nice cat/sed, we could remove the day & month and replace the comma
# with a space.
#

cat file_dstat | sed -e "s/27-11 //g" | sed -e "s/,/ /g" > file_dstat_tmp
mv file_dstat_tmp file_dstat


