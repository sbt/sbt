/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

private[sbt] trait IOSyntax0 extends IOSyntax1 {
  implicit def alternative[A, B](f: A => Option[B]): Alternative[A, B] = new Alternative[A, B] {
    override def |(g: A => Option[B]): A => Option[B] = (a: A) => f(a) orElse g(a)
  }
}
private[sbt] sealed trait IOSyntax1 extends sbt.io.IOSyntax with sbt.nio.file.syntax0
private[sbt] sealed trait Alternative[A, B] {
  def |(g: A => Option[B]): A => Option[B]
}
