/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

import java.util.List;

/**
 * A minimal representatin of the `WorkspaceEdit` found in the LSP protocol.
 *
 * <p>However it only supports the minimal `changes` to ensure the fixes will work with all clients.
 *
 * <p>NOTE: In the future this may be expanded to handle resource operations via `documentChanges`.
 *
 * @see <a href=
 *     "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#workspaceEdit">`WorkspaceEdit`</a>
 */
public interface WorkspaceEdit {

  /** List of [[xsbti.TextEdit]] that belong to this WorkspaceEdit. */
  List<TextEdit> changes();
}
