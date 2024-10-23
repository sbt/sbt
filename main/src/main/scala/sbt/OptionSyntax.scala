/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.util.Optional

// Todo: port this back to Zinc in place of JavaInterfaceUtil.
trait OptionSyntax {

  /** Injects some method. */
  extension [A](a: A) {
    def some: Option[A] = Some(a)
  }

  /** Injects asScala method. */
  extension [A](optional: Optional[A]) {
    def asScala: Option[A] =
      if (!optional.isPresent) None
      else Some(optional.get())
  }

  /** Injects asJava method. */
  extension [A](option: Option[A]) {
    def asJava: Optional[A] = option match {
      case Some(value) => Optional.of(value)
      case None        => Optional.empty[A]
    }
  }

  final def none[A]: Option[A] = None
}

object OptionSyntax extends OptionSyntax
