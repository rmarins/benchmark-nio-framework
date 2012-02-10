var http = require('http'),
    sys = require('util'),
	fs = require('fs'),
	mime = require('./content-type');

function serve_hardcoded(resp) {
	write_headers(resp, 200, {
		'Content-Length': 45,
	    'Content-Type': 'text/html; charset=utf-8'
	});
	var data = new Buffer('<html><body><h1>Olá Mundo!</h1></body></html>');
	resp.write(data, 'utf-8');
	resp.end();
}

function serve_static_file(path, req, resp) {

    path = '/Users/rmarins/Sites' + path;
	fs.stat(path, function (err, stats) {
        if (err) {
            // ENOENT is normal on 'file not found'
            if (err.errno != process.ENOENT) { 
                // any other error is abnormal - log it
                console.log("fs.stat(" + path + ") failed: " + err); 
            }
            return file_not_found();
        }
        if (stats.isDirectory()) {
            if (path.match(/\/$/)) {
                return serve_static_file(path + "/index.html", req, resp);
            }
            else {
                var redirect_host = req.headers.host ? ('http://' + req.headers['host']) : '';
                var redirect_path = path + "/" + (path.search || "");
                return redirect_302_found(redirect_host + redirect_path);
            }
        }
        if (!stats.isFile()) {
            return file_not_found();
        } else {
            var if_modified_since = req.headers['if-modified-since'];
            if (if_modified_since) {
                var req_date = new Date(if_modified_since);
                if (stats.mtime <= req_date && req_date <= Date.now()) {
                    return not_modified();
                }
                else stream_file(path, stats);
            } else if (req.method == 'HEAD') {
                write_headers(resp, 200, {
                    'Content-Length':stats.size, 
                    'Content-Type':mime.mime_type(path),
                    'Last-Modified': stats.mtime
                });
                resp.end('');
            } else {
                return stream_file(path, stats);
            }
        }
	});

    function stream_file(file, stats) {
//        console.log('Streaming file... ' + file);
        try {
            var readStream = fs.createReadStream(file);
//            console.log('stream created ' + readStream);
        } 
        catch (err) {
            return file_not_found();
        }

        write_headers(resp, 200, {
            'Content-Length':stats.size, 
            'Content-Type':mime.mime_type(file),
            'Last-Modified':stats.mtime
        });

//        console.log('headers written');

        sys.pump(readStream, resp, function(err) {
            if (err) {
                console.log('stream pump error... ' + err);
            }
        });

        req.connection.addListener('timeout', function() {
            /* dont destroy it when the fd's already closed */
            if (readStream.readable) {
                readStream.destroy();
            }
        });

        readStream.addListener('error', function (err) {
            resp.end('');
        });
        resp.addListener('error', function (err) {
            readStream.destroy();
        });
    }

    function not_modified() {
    	send_error(resp, 304);
    }

    function file_not_found() {
        var body = "404: " + req.url + " not found.\n";
        send_error(resp, 404, body);
    }

    function server_error(message) {
    	send_error(resp, 500, message);
    }

    function redirect_302_found(location) {
        write_headers(resp, 302, {
            'Location' : location
        });
        resp.end('');
    }
}

function send_error(resp, httpstatus, body) {
	write_headers(resp, httpstatus, {
		'Content-Length': (body ? body.length : 0),
		'Content-Type': "text/plain"
	});
	if (body && resp.method !== 'HEAD' && body.length > 0) {
		resp.end(body);
	} else {
		resp.end('');
	}
}

function write_headers(resp, httpstatus, headers) {
    headers = headers || {};
    headers["Server"] = "rafaelmarins.com/Benchmark_v1.0.0 Node.js/0.6.10";
    headers["Date"] = (new Date()).toUTCString();

    resp.writeHead(httpstatus, headers);
}

http.createServer(function(req, resp) {

	var serveHardcodedRegex = /^\/hardcoded\/(.*)/i;
	var serveStaticRegex = /^\/files\/(.*)/i;

	if (serveHardcodedRegex.test(req.url)) {
		serve_hardcoded(resp);
	} else if (serveStaticRegex.test(req.url)) {
		var path = req.url.substr(6);
//		console.log(path);
		serve_static_file(path, req, resp);
	} else {
		send_error(resp, 400, 'Bad request: ' + req.url);
	}

}).listen(8080);

console.log('Server running at http://127.0.0.1:8080/');
