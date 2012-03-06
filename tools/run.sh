#!/bin/sh
#

PRG="$0"
CWD=`pwd`
BENCHMARK_HOME="`dirname ${PRG}`/.."
BENCHMARK_LOAD="4 100 3 1000 2 5000 1 10000"

BASE_URL="http://localhost:8080"

DSTAT_OUT='dstat.dat'

DSTAT_EXEC=`which dstat`
MAVEN_EXEC=`which mvn`
NODE_EXEC=`which node`
AB_EXEC=`which ab`

#
# Prepare the process status logging file path
#

_get_ps_logfile() #@ USAGE: _get_ps_logfile <server_name>
{
  _GET_PS_LOGFILE="${CWD}/${1}_process.log"
}

#
# Add benchmark log entry in the process status file
#

log_benchmark() #@ USAGE: log_benchmark <context> <server_name>
                #             <no_connections> <no_concurrency>
{
  _get_ps_logfile "$2"
  logfile=$_GET_PS_LOGFILE
  printf "\n\n" >> $logfile
  date >> $logfile
  printf "Benchmarking $1 against $2 with $3/$4 load.\n" >> $logfile
}


_fibonacci() #@ USAGE _fibonacci <num>
{
	f1=0
	f2=1

  i=0
  while [ $i -le $1 ]; do
		fn=$((f1+f2))
		f1=$f2
		f2=$fn
		i=$(( i + 1 ))
	done

	_FIBONNACI=$fn
}


#
# Run the benchmark
#

run_hardcore_benchmark() #@ USAGE: run_hardcore_benchmark <server_name>
                         #             <no_connections> <no_concurrency>
{
  context_path="/hardcore/test"
  log_benchmark "$context_path" "$1" "$2" "$3"

  sleep 30 # wait a while before stressing the server

  # Apache Benchmarking tool
  ##
  output_file_prefix="ab_hardcoded_$1_$3"
 
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}" >> "${CWD}/${output_file_prefix}.out" 2>&1

}

run_static_benchmark() #@ USAGE: run_static_benchmark <server_name>
                         #             <no_connections> <no_concurrency>
{
  context_path="/files/"
  log_benchmark "$context_path" "$1" "$2" "$3"

  sleep 30

  output_file_prefix="ab_hardcoded_16kb_$1_$3"
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}file16.kb" >> "${CWD}/${output_file_prefix}.out"

  sleep 30

  output_file_prefix="ab_hardcoded_35kb_$1_$3"
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}file35.kb" >> "${CWD}/${output_file_prefix}.out" \
     2>&1

  sleep 30

  output_file_prefix="ab_hardcoded_116kb_$1_$3"
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}file116.kb" >> "${CWD}/${output_file_prefix}.out" \
		 2>&1

  sleep 30

  output_file_prefix="ab_hardcoded_511kb_$1_$3"
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}file511.kb" >> "${CWD}/${output_file_prefix}.out" \
		 2>&1

  sleep 30

  output_file_prefix="ab_hardcoded_1mb_$1_$3"
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}file1.mb" >> "${CWD}/${output_file_prefix}.out" \
		 2>&1

  sleep 30

  output_file_prefix="ab_hardcoded_2mb_$1_$3"
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}file2.mb" >> "${CWD}/${output_file_prefix}.out" \
		 2>&1

  sleep 30

  output_file_prefix="ab_hardcoded_20mb_$1_$3"
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}file20.mb" >> "${CWD}/${output_file_prefix}.out" \
		 2>&1

  sleep 30

  output_file_prefix="ab_hardcoded_221mb_$1_$3"
  $AB_EXEC -n $2 -c $3 -g "${CWD}/${output_file_prefix}.dat" \
    "${BASE_URL}${context_path}file221.mb" >> "${CWD}/${output_file_prefix}.out" \
		 2>&1

}

run_benchmark() #@ USAGE: run_benchmark <server_name>
{
  printf "Running the benchmark...\n"

  for concurrency in $BENCHMARK_LOAD; do
		_fibonacci $concurrency
    run_hardcore_benchmark $1 $(( $concurrency * 30 )) $concurrency
  done

  for concurrency in $BENCHMARK_LOAD; do
    run_static_benchmark $1 $(( $concurrency * 30 )) $concurrency
  done

}

#
# Start process status monitoring to file
_do_process_status() #@ do_process_status <pid> <server_name> [interval]
{
  _get_ps_logfile "$2"
  logfile=$_GET_PS_LOGFILE

  (while true; do ps -p $1 -o comm,nlwp,%cpu,%mem,vsz,time --no-headers \
      >> $logfile; sleep ${3:-10}; done ) &

  _DO_PROCESS_STATUS=$!
}

#
# Parse command line arguments
##

doc_root="${BENCHMARK_HOME}/static"

#
# Start collecting overall system stats with dstat, watching the time, memory,
# cpu, loopback device with all the values in integer format; see the dstat man
# page for more details about the options !
##

$DSTAT_EXEC -tm -c -d --aio -n -N lo,total --integer \
    --output $DSTAT_OUT >> /dev/null &
dstat_pid=$!

#
# Start/stop Netty HTTP server and run the benchmark
##

cd $BENCHMARK_HOME/netty

export MAVEN_OPTS="-DdocRoot=${doc_root} -Dthreading=true -server -Xms64m \
    -Xmx64m -XX:+UseParallelGC -XX:+AggressiveOpts -XX:+UseFastAccessorMethods \
    -XX:MaxPermSize=32m -XX:PermSize=16m"

$MAVEN_EXEC clean compile exec:java >> "${CWD}/stdout_netty.log" 2>&1 &
netty_pid=$!
printf "Netty is running on pid %s\n" "$netty_pid"

_do_process_status $netty_pid 'netty'
ps_pid=$_DO_PROCESS_STATUS

run_benchmark "netty"

kill $ps_pid $netty_pid # stops Netty
sleep 60

#
# Start/stop Node.js HTTP server and run the benchmark
##

cd $BENCHMARK_HOME/nodejs
export NODE_PATH=".:/usr/lib/node_modules:${NODE_PATH}"

$NODE_EXEC HttpServer.js --doc-root ${doc_root} >> ${CWD}/stdout_nodejs.log 2>&1 &
node_pid=$!
printf "Node.js is running on pid %s\n" "$node_pid"

_do_process_status $node_pid 'nodejs'
ps_pid=$_DO_PROCESS_STATUS

run_benchmark "nodejs"

kill $ps_pid $node_pid # stops Node.js

#
# Stop dstat
##
kill $dstat_pid

