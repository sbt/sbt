/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

/**
 * A representation of the `TextEdit` found in the LSP protocol.
 *
 * <p>NOTE: That instead of a `Range` we use the internal [[xsbti.Position]].
 *
 * @see <a
 *     href="https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/#textEdit">`TextEdit`</a>
 */
public interface TextEdit {

  /** The position this edit will be applied to. */
  Position position();

  /** The next text that will be inserted into the given [[TextEdit.position]]. */
  String newText();
}
