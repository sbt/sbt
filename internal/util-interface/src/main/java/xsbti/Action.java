/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

import java.util.Optional;

/**
 * An Action is very miminal representation of a `CodeAction` in the LSP protocol.
 *
 * <p>However it only focuses on the actual title, description, and edit, leaving it up to the
 * language server to communicate with the client and put together a proper codeAction in accordance
 * to client capabilities.
 *
 * @see <a href=
 *     "https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#codeAction">`CodeAction`</a>
 */
public interface Action {

  /** Title of the action that will be shown to the user client side. */
  String title();

  /** Optional description that may be shown to the user client side to explain the action. */
  Optional<String> description();

  /** The actual edit contained in the action. */
  WorkspaceEdit edit();
}
