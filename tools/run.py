#!/usr/bin/env python
#
import os.path,subprocess,threading,time,sys
from subprocess import CalledProcessError

DOC_ROOT = '/home/rmarins/Downloads/python-2.7.2-docs-html'

# With dstat, we're watching the memory and the swap metrics with all
# the values in integer format; see the dstat man page for more details
# about the options !
##
DSTAT_CMD = 'dstat -tms -N lo,total --integer --output {output}'

# Apache Benchmarking tool
##
AB_CMD = 'ab -r -n {requests} -c {concurrency} http://localhost:8080/{context_path}'

config = [ {'name' : 'netty', 
             'exec' : 'mvn clean compile exec:java',
              'env' : { 'MAVEN_OPTS' : '-DdocRoot=%s -server -Xms64m -Xmx64m -XX:+UseParallelGC -XX:+AggressiveOpts -XX:+UseFastAccessorMethods -XX:MaxPermSize=32m -XX:PermSize=16m' % (DOC_ROOT) } },
            {'name' : 'nodejs', 
             'exec' : 'node HttpServer.js %s' % (DOC_ROOT) } ]

def get_filepath(fname):
    return os.path.normpath(os.getcwd() + '/' + fname)

def get_prepared_callargs(cmd):
    cargs = cmd.split()
    cargs.insert(0, '/usr/bin/env')
    return cargs

def collect_ps_data(pid, proc_stats_file, polling_interval=10):

    proc_stats_args = ['/usr/bin/env', 'ps',
               '-p', str(pid), '-o', 'comm,nlwp,%cpu,%mem,time', '--no-headers'
               '>>', proc_stats_file]

    while True:
        try:
            subprocess.check_call( proc_stats_args )
            time.sleep(polling_interval)
        except CalledProcessError:
            break

    return

def do_proc_monitor(name, pid, output):
    """ Start the monitoring thread for the given process """
    monitor = threading.Thread( target=collect_ps_data(pid, output) )
    monitor.start()
    return monitor

def run_benchmark(name, cmd, envvars):

    server_callargs = get_prepared_callargs(cmd);
    server_proc = subprocess.Popen( server_callargs, env=envvars )

    do_proc_monitor(name, server_proc.pid, get_filepath('{}_proc_data'.format( name )))

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
        run_benchmark(server['name'], server['exec'], server['env'])

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
