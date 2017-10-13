'use strict';

import * as path from 'path';
import { ExtensionContext, workspace, commands, TextEditor, window, Range, TextEditorRevealType, Selection } from 'vscode';
import { LanguageClient, LanguageClientOptions, ServerOptions, TransportKind } from 'vscode-languageclient';
import { ScalaOutlineProvider } from './ScalaOutlineProvider';

let fs = require('fs');

export function activate(context: ExtensionContext) {
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
			return {
				token: discoverToken()
			};
		}
	}
		
	// the port file is hardcoded to a particular location relative to the build.
	function discoverToken(): String {
		let pf = path.join(workspace.rootPath, 'project', 'target', 'active.json');
		let portfile = JSON.parse(fs.readFileSync(pf));
		let tf = portfile.tokenfilePath;
		let tokenfile = JSON.parse(fs.readFileSync(tf));
		return tokenfile.token;
	}

	// Create the language client and start the client.

	let lc = new LanguageClient('lspSbtScala', 'sbt Scala Language Server', serverOptions, clientOptions)
	let disposable = lc.start();
	context.subscriptions.push(disposable);

	registerScalaOutline(context, lc);
}

function registerScalaOutline(context: ExtensionContext, lc: LanguageClient) {
	let p = new ScalaOutlineProvider(context, lc);
	window.registerTreeDataProvider('scalaOutline', p);
	commands.registerCommand("scalaOutline.select", (editor: TextEditor, range: Range) => {
		editor.revealRange(range, TextEditorRevealType.Default)
		editor.selection = new Selection(range.start, range.end);
		commands.executeCommand("workbench.action.focusActiveEditorGroup");
	})
	commands.registerCommand("scalaOutline.refresh", () => {
		p.refresh();
	})
}