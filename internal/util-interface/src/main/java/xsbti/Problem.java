/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

public interface Problem {
  String category();

  Severity severity();

  String message();

  Position position();

  // Default value to avoid breaking binary compatibility
  /**
   * If present, the string shown to the user when displaying this Problem. Otherwise, the Problem
   * will be shown in an implementation-defined way based on the values of its other fields.
   */
  default Optional<String> rendered() {
    return Optional.empty();
  }

  /**
   * The unique code attached to the diagnostic being reported.
   *
   * <p>NOTE: To avoid breaking compatibility we provide a default to account for older Scala
   * version that do not have codes.
   */
  default Optional<DiagnosticCode> diagnosticCode() {
    return Optional.empty();
  }

  /**
   * The possible releated information for the diagnostic being reported.
   *
   * <p>NOTE: To avoid breaking compatibility we provide a default to account for older Scala
   * version that do not have the concept of "related information".
   */
  default List<DiagnosticRelatedInformation> diagnosticRelatedInforamation() {
    return Collections.emptyList();
  }
}
