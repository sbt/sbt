'use strict';

import * as path from 'path';
import * as url from 'url';
let net = require('net'),
  fs = require('fs'),
  stdin = process.stdin,
  stdout = process.stdout;

let u = discoverUrl();

let socket = net.Socket();
socket.on('data', (chunk: any) => {
  // send it back to stdout
  stdout.write(chunk);
}).on('end', () => {
  stdin.pause();
});
socket.connect(u.port, '127.0.0.1');

stdin.resume();
stdin.on('data', (chunk: any) => {
  socket.write(chunk);
}).on('end', () => {
  socket.end();
});

// the port file is hardcoded to a particular location relative to the build.
function discoverUrl(): url.Url {
  let pf = path.join(process.cwd(), 'project', 'target', 'active.json');
  let portfile = JSON.parse(fs.readFileSync(pf));
  return url.parse(portfile.uri);
}
