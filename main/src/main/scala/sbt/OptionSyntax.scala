/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import java.util.Optional

// Todo: port this back to Zinc in place of JavaInterfaceUtil.
trait OptionSyntax {
  import OptionSyntax._
  implicit def sbtOptionSyntaxRichOptional[A](optional: Optional[A]): RichOptional[A] =
    new RichOptional[A](optional)

  implicit def sbtOptionSyntaxRichOption[A](option: Option[A]): RichOption[A] =
    new RichOption[A](option)

  implicit def sbtOptionSyntaxOptionIdOps[A](a: A): OptionIdOps[A] =
    new OptionIdOps[A](a)

  final def none[A]: Option[A] = None
}

object OptionSyntax extends OptionSyntax {

  /** Injects some method. */
  final class OptionIdOps[A](val a: A) extends AnyVal {
    def some: Option[A] = Some(a)
  }

  /** Injects asScala method. */
  final class RichOptional[A](val optional: Optional[A]) extends AnyVal {
    def asScala: Option[A] =
      if (!optional.isPresent) None
      else Some(optional.get())
  }

  /** Injects asJava method. */
  final class RichOption[A](val option: Option[A]) extends AnyVal {
    def asJava: Optional[A] = option match {
      case Some(value) => Optional.of(value)
      case None        => Optional.empty[A]
    }
  }
}
