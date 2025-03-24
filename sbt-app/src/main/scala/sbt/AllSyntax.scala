/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

private[sbt] trait IOSyntax0 extends IOSyntax1 {
  extension [A, B](f: A => Option[B]) {
    def |(g: A => Option[B]): A => Option[B] = (a: A) => f(a) orElse g(a)
  }
}
private[sbt] sealed trait IOSyntax1 extends sbt.io.IOSyntax with sbt.nio.file.syntax0
