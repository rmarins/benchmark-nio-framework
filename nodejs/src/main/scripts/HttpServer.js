var http = require('http');

http.createServer(function(req, res) {
    res.writeHead(200, {"Content-Length": "47",
                        "Content-Type": "text/html; charset=utf-8"});
    res.write("<html><body><h1>hello world</h1></body></html>");
    res.end();
}).listen(8080);

console.log('Server running at http://127.0.0.1:8080/');

