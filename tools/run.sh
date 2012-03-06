#!/bin/sh
#

PRG="$0"
CWD=`pwd`
BENCHMARK_HOME="`dirname ${PRG}`/.."
BENCHMARK_LOAD="1 1 2 4 7 13 24 44 81 149 274 504 927 1705 3136 5768 10609"

BASE_URL="http://localhost:8080"

DSTAT_OUT='system_stat.dat'

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
  sleep 10 # wait a while before stressing the server

  context_path="/hardcore/test"
  log_benchmark "$context_path" "$1" "$2" "$3"

  # Apache Benchmarking tool
  ##
  output_file_prefix="ab_hardcoded_$1_$3"
 
  $AB_EXEC -kr -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/${output_file_prefix}.out" 2>&1

}

run_static_benchmark() #@ USAGE: run_static_benchmark <server_name>
                         #             <no_connections> <no_concurrency>
{

  sleep 5
  context_path="/files/file512.b"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_512b_$1_$3.out"

  sleep 5
  context_path="/files/file1.kb"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_1kb_$1_$3.out"

  sleep 5
  context_path="/files/file16.kb"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_16kb_$1_$3.out"

  sleep 5
  context_path="/files/file256.kb"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_256kb_$1_$3.out"

  sleep 5
  context_path="/files/file512.kb"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_512kb_$1_$3.out"

  sleep 5
  context_path="/files/file1.mb"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_1mb_$1_$3.out"

  sleep 10
  context_path="/files/file8.mb"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_8mb_$1_$3.out"

  sleep 10
  context_path="/files/file64.mb"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_64mb_$1_$3.out"

  sleep 10
  context_path="/files/file128.mb"
  log_benchmark "$context_path" "$1" "$2" "$3"

  $AB_EXEC -n $2 -c $3 "${BASE_URL}${context_path}" >> "${CWD}/ab_static_128mb_$1_$3.out"

}

run_benchmark() #@ USAGE: run_benchmark <server_name>
{
  printf "Running the benchmark...\n"

  for concurrency in $BENCHMARK_LOAD; do
    run_hardcore_benchmark $1 $(( $concurrency * 1024 )) $concurrency
  done

#  for concurrency in $BENCHMARK_LOAD; do
#    run_static_benchmark $1 $(( $concurrency * 128 )) $concurrency
#  done

}

#
# Start process status monitoring to file
_do_process_status() #@ do_process_status <pid> <server_name> [interval]
{
  _get_ps_logfile "$2"
  logfile=$_GET_PS_LOGFILE

  (while true; do ps -p $1 -o comm,nlwp,%cpu,%mem,vsz,time --no-headers \
      >> $logfile; sleep ${3:-1}; done ) &

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

$DSTAT_EXEC -tm -c -d --aio -n -N lo,total --integer --output $DSTAT_OUT >> /dev/null &
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
sleep 30

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

