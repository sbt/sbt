/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

import java.net.URI;
import java.util.List;

/** A collection of TextEdits that belong to a given URI. */
public interface FileChanges {

  /** The URI that the edits belong to. */
  URI uri();

  /** The edits belonging to the URI. */
  List<TextEdit> edits();
}
