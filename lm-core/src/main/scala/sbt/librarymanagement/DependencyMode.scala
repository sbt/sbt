/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.librarymanagement

/**
 * Controls which managed dependencies appear on the classpath.
 *
 * This setting is used to enforce explicit dependency declarations
 * by restricting the classpath to only include dependencies at a
 * specified depth level.
 */
enum DependencyMode:

  /** All transitive dependencies are included (default, current behavior). */
  case Transitive

  /** Only direct dependencies plus scala-library are included. */
  case Direct

  /** Direct dependencies plus their immediate transitive dependencies plus scala-library. */
  case PlusOne

end DependencyMode
