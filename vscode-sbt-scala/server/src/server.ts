'use strict';

import * as path from 'path';
import * as url from 'url';
import * as net from 'net';
let fs = require('fs'),
  os = require('os'),
  stdin = process.stdin,
  stdout = process.stdout;

let socket = new net.Socket();
socket.on('data', (chunk: any) => {
  // send it back to stdout
  stdout.write(chunk);
}).on('end', () => {
  stdin.pause();
});
connectSocket(socket);

stdin.resume();
stdin.on('data', (chunk: any) => {
  socket.write(chunk);
}).on('end', () => {
  socket.end();
});

function connectSocket(socket: net.Socket):　net.Socket {
  let u = discoverUrl();
  // let socket = net.Socket();
  if (u.protocol == 'tcp:') {
    socket.connect(+u.port, '127.0.0.1');
  } else if (u.protocol == 'local:' && os.platform() == 'win32') {
    let pipePath = '\\\\.\\pipe\\' + u.hostname;
    socket.connect(pipePath);
  } else if (u.protocol == 'local:') {
    socket.connect(u.path);
  } else {
    throw 'Unknown protocol ' + u.protocol;
  }
  return socket;
}

// the port file is hardcoded to a particular location relative to the build.
function discoverUrl(): url.Url {
  let pf = path.join(process.cwd(), 'project', 'target', 'active.json');
  let portfile = JSON.parse(fs.readFileSync(pf));
  return url.parse(portfile.uri);
}
