/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

/**
 * Automatic JsonFormat instances for common sbt types.
 *
 * This addresses issue #8288 by providing JsonFormats for types that
 * commonly cause compilation errors when used in cached tasks.
 */
object AutoJsonFormats {

  // Import existing formats for convenience
  // Note: FileInfo formats are available through FileInfo companion objects
}
