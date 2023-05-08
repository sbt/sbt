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
   * versions that do not have codes.
   */
  default Optional<DiagnosticCode> diagnosticCode() {
    return Optional.empty();
  }

  /**
   * The possible releated information for the diagnostic being reported.
   *
   * <p>NOTE: To avoid breaking compatibility we provide a default to account for older Scala
   * versions that do not have the concept of "related information".
   */
  default List<DiagnosticRelatedInformation> diagnosticRelatedInforamation() {
    return Collections.emptyList();
  }

  /**
   * Actions (aka quick fixes) that are able to either fix or address the issue that is causing this
   * Problem.
   *
   * <p>For example given the following code:
   *
   * <pre>
   *  trait Example:
   *    def foo(): Unit
   *    def bar(): Unit
   *
   *  class MyExample extends Example
   * </pre>
   *
   * You could expect this to have multiple actions attatched:
   *
   * <ul>
   *   <li>An option to implement a stub method for `foo()` and `bar()`
   *   <li>An option to make `MyExample` abstract
   * </ul>
   */
  default List<Action> actions() {
    return Collections.emptyList();
  }
}
