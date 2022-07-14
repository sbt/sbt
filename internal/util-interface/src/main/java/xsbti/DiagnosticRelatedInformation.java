/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

/**
 * Related information for a given diagnostic. At times this can be another place in your code
 * contributing to the diagnostic or just relevant code relating to the diagnostic.
 */
public interface DiagnosticRelatedInformation {
  /** Position of the related information */
  Position position();

  /** Message indicating why this related information is attached to the diagnostic. */
  String message();
}
