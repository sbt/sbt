import {
    LanguageClient,
    CancellationTokenSource
} from 'vscode-languageclient';
import {
    ExtensionContext,
    TreeDataProvider,
    EventEmitter,
    TreeItem,
    TextEditor,
    window,
    TreeItemCollapsibleState,
    Range,
    Position,
} from 'vscode';

/**
 * Provides functionality for a Outline view that is supposed to hold
 * information about Scala code.
 */
export class ScalaOutlineProvider implements TreeDataProvider<OutlineNode> {
    private emitter = new EventEmitter<OutlineNode | null>();
    readonly onDidChangeTreeData = this.emitter.event;

    private context: ExtensionContext;
    private connection: LanguageClient;
    private rootNodes: OutlineNode[];
    private editor: TextEditor;

    constructor(context: ExtensionContext, connection: LanguageClient) {
        this.context = context;
        this.connection = connection;
        this.rootNodes = [];
    }

    getTreeItem(node: OutlineNode): TreeItem {
        let i = new TreeItem(node.str);
        if (node.children.length === 0)
            i.collapsibleState = TreeItemCollapsibleState.None;
        else
            i.collapsibleState = TreeItemCollapsibleState.Collapsed;
        // TODO don't hardcode the range here
        let range = new Range(new Position(2, 5), new Position(5, 5));
        i.command = {
            command: "scalaOutline.select",
            title: "",
            arguments: [this.editor, range]
        }
        return i;
    }

    async getChildren(node?: OutlineNode): Promise<OutlineNode[]> {
        if (node)
            return node.children;
        else {
            return this.rootNodes;
        }
    }

    /**
     * Refreshes the Scala Outline with the most recent data.
     */
    refresh() {
        this.updateNodes();
        this.emitter.fire();
    }

    private async updateNodes(): Promise<void> {
        this.editor = window.activeTextEditor;
        let ts = new CancellationTokenSource;
        let resp = await this.connection.sendRequest<string[]>("sbt/dependencies", ts.token)
        this.rootNodes = resp.map(n => new OutlineNode(n))
    }
}

/**
 * A data class that holds data of a single entry that can be shown in the
 * Scala Outline view.
 */
class OutlineNode {
    children?: OutlineNode[];
    str: string;

    constructor(str: string, children?: OutlineNode[]) {
        this.children = children ? children : [];
        this.str = str;
    }

    addChild(child: OutlineNode) {
        this.children.push(child);
    }

    toString(): string {
        return `OutlineNode(${this.str})`;
    }
}
