'use strict';

import * as path from 'path';
import * as url from 'url';
import * as net from 'net';
let fs = require('fs'),
	os = require('os');
import * as vscode from 'vscode';
import { ExtensionContext, workspace } from 'vscode'; // workspace, 
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient';

let terminal: vscode.Terminal = null;

function delay(ms: number) {
	return new Promise(resolve => setTimeout(resolve, ms));
}

export async function deactivate() {
	if (terminal != null) {
		terminal.sendText("exit");
		await delay(1000);
		terminal.dispose();
	}
}

export async function activate(context: ExtensionContext) {
	// Start sbt
	terminal = vscode.window.createTerminal(`sbt`);
	terminal.show();
	terminal.sendText("sbt");
	// Wait for SBT server to start
	let retries = 60;
	while (retries > 0) {
		retries--;
		await delay(1000);
		if (isServerUp()) {
			break;
		}
	}

	// The server is implemented in node
	let serverModule = context.asAbsolutePath(path.join('server', 'server.js'));
	// The debug options for the server
	// let debugOptions = { execArgv: ["--nolazy", "--debug=6009"] };
	
	// If the extension is launched in debug mode then the debug server options are used
	// Otherwise the run options are used
	let serverOptions: ServerOptions = {
		run : { module: serverModule, transport: TransportKind.stdio },
		debug: { module: serverModule, transport: TransportKind.stdio }
	}
	
	// Options to control the language client
	let clientOptions: LanguageClientOptions = {
		documentSelector: [{ language: 'scala', scheme: 'file' }, { language: 'java', scheme: 'file' }],
		initializationOptions: () => { 
			return discoverToken();
		}
	}

	// Don't start VS Code connection until sbt server is confirmed to be up and running.
	function isServerUp(): boolean {
		let isFileThere = fs.existsSync(path.join(workspace.rootPath, 'project', 'target', 'active.json'));
		if (!isFileThere) {
			return false;
		} else {
			let skt = new net.Socket();
			try {
				connectSocket(skt);
			} catch(e) {
				return false;
			}
			skt.end();
			return true;
		}
	}

	function connectSocket(socket: net.Socket): 　net.Socket {
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

	// the port file is hardcoded to a particular location relative to the build.
	function discoverToken(): any {
		let pf = path.join(workspace.rootPath, 'project', 'target', 'active.json');
		let portfile = JSON.parse(fs.readFileSync(pf));

		// if tokenfilepath exists, return the token.
		if (portfile.hasOwnProperty('tokenfilePath')) {
			let tf = portfile.tokenfilePath;
			let tokenfile = JSON.parse(fs.readFileSync(tf));
			return {
				token: tokenfile.token
			};
		} else {
      return {};
		}
	}

	// Create the language client and start the client.
	let disposable = new LanguageClient('lspSbtScala', 'sbt Scala Language Server', serverOptions, clientOptions).start();
	
	context.subscriptions.push(disposable);
}
