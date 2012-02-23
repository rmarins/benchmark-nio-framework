#!/usr/bin/env python
#
import os.path,subprocess,threading,time,sys
from subprocess import CalledProcessError

DOC_ROOT = '/home/rmarins/Downloads/python-2.7.2-docs-html'

# With dstat, we're watching the memory and the swap metrics with all
# the values in integer format; see the dstat man page for more details
# about the options !
##
DSTAT_CMD = 'dstat -tms -c -C 0,1,2,3,4,5,6,7,total -n -N lo,total --integer --output {output} >> /dev/null'

# Apache Benchmarking tool
##
AB_CMD = 'ab -k -n {requests} -c {concurrency} -g ab_{type}_{server_name}_{concurrency}.dat http://localhost:8080{context_path} >> ab_{type}_{server_name}_{concurrency}.out'

# Process Status marker
##
PSMARKER_CMD = 'echo "\n\n`date`\nBenchmarking {url} against {sname} HTTP server with #{concurrency} param" >> {output}'

# Number of concurrent connections
##
BENCHMARK_PARAMS = [ 1, 5, 10, 20, 50, 100, 200, 500, 1000, 2000, 5000, 7000, 10000 ]

config = [ {'name' : 'netty', 
            'exec' : 'mvn clean compile exec:java',
             'env' : { 'MAVEN_OPTS' : '-DdocRoot=%s -server -Xms64m -Xmx64m -XX:+UseParallelGC -XX:+AggressiveOpts -XX:+UseFastAccessorMethods -XX:MaxPermSize=32m -XX:PermSize=16m' % (DOC_ROOT) }
		   },
           {'name' : 'nodejs', 
            'exec' : 'node HttpServer.js %s' % (DOC_ROOT),
		     'env' : { 'NODE_PATH' : '/usr/lib/node_modules' }
		   } ]

collect_process_status = True

def get_filepath(fname):
    return os.path.normpath(os.getcwd() + '/' + fname)

def get_ps_logfile(sname):
	return get_filepath('{}_process_data'.format( sname ))

def get_prepared_callargs(cmd):
    cargs = cmd.split()
    cargs.insert(0, '/usr/bin/env')
    return cargs

def collect_ps_data(pid, proc_stats_file, polling_interval=10):

    proc_stats_args = ['/usr/bin/env', 'ps',
               '-p', str(pid), '-o', 'comm,nlwp,%cpu,%mem,vsz,time', '--no-headers'
               '>>', proc_stats_file]

	collect_process_status = True

    while collect_process_status:
        try:
            subprocess.check_call( proc_stats_args )
            time.sleep(polling_interval)
        except CalledProcessError:
            break

    return

def do_process_status(name, pid, output):
    """ Start the monitoring thread for the given process """
    monitor = threading.Thread( target=collect_ps_data(pid, output) )
    monitor.start()
    return monitor

def start_server(name, cmd, envvars):

	server_dir = get_filepath(name)
    server_callargs = get_prepared_callargs(cmd)
    server_proc = subprocess.Popen( server_callargs, cwd=server_dir, env=envvars )

    ps_thread = do_process_status(name, server_proc.pid, get_ps_logfile(name))

    return server_proc

def stop_server(server_proc):

	collect_process_status = False
	server_proc.terminate()

def run_benchmark(server_name):

	for concurrency in BENCHMARK_PARAMS:
		run_hardcoded_benchmark(server_name, concurrency * 1000, concurrency)

	return

def run_hardcoded_benchmark(server_name, no_requests, no_concurrency):

	context = '/hardcoded/test'
	log_benchmark(context, server_name, no_requests, no_concurrency)

	cmd = AB_CMD.format(type='hardcoded', server_name=server_name, context_path=context, requests=no_requests, concurrency=no_concurrency)
	cmd_args = get_prepared_callargs(cmd)

	try:
		subproces.check_call( cmd_args )
	except CalledProcessError:
		# ignore

	return

def log_benchmark(url, server_name, requests, concurrency):

    echo_args = ['/usr/bin/env', 'echo',
               '"\n\n`date`\nBenchmarking {context} against {server_name} with #{requests}/{concurrency} load"'.format(context=url, server_name=server_name, requests=requests, concurrency=concurrency),
               '>>', get_ps_logfile(server_name) ]

    try:
        subprocess.check_call( echo_args )
    except CalledProcessError:
        # ignore

	return

def main():

    # Start collecting overall system stats
    ##    
    dstat_data_file = get_filepath('dstat_data')
    dstat_callargs = get_prepared_callargs(DSTAT_CMD.format(output=dstat_data_file))

    dstat_proc = subprocess.Popen( dstat_callargs )

    # Run benchmark for each configured server process
    ##    
    for server in config:
        pt = start_server(server['name'], server['exec'], server['env'])
		run_benchmark(server['name'])
		stop_server(pt)

	dstat_proc.terminate()

    ##
    # the dstat file was at this format
    
    # "date/time","used","buff","cach","free","used","free"
    # 27-11 19:44:09,449216512.0,104566784.0,464535552.0,42463232.0,0.0,0.0
    # 27-11 19:44:10,449376256.0,104566784.0,464535552.0,42303488.0,0.0,0.0
    
    ##
    # After a nice cat/sed, we could remove the day & month and replace the comma
    # with a space.
    #
    
#    cat file_dstat | sed -e "s/27-11 //g" | sed -e "s/,/ /g" > file_dstat_tmp
#    mv file_dstat_tmp file_dstat
    
main()
