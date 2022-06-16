/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package xsbti;

import java.util.Optional;

/**
 * A DiagnosticCode is a unique identifier that the compiler can associate with a diagnostic. This
 * is useful for tools to be able to quickly identify what diagnostic is being reported without
 * having to rely on parsing the actual diagnostic message, which might not be stable.
 */
public interface DiagnosticCode {
  /** The unique code. This is typically in the format of E000 */
  String code();

  /** Possible explanation to explain the meaning of the code */
  Optional<String> explanation();
}
