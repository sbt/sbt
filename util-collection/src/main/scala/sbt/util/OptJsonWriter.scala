/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.util

import sjsonnew.JsonWriter

sealed trait OptJsonWriter[A]
final case class NoJsonWriter[A]() extends OptJsonWriter[A]
final case class SomeJsonWriter[A](value: JsonWriter[A]) extends OptJsonWriter[A]

trait OptJsonWriter0 {
  implicit def fallback[A]: NoJsonWriter[A] = NoJsonWriter()
}
object OptJsonWriter extends OptJsonWriter0 {
  implicit def lift[A](using z: JsonWriter[A]): SomeJsonWriter[A] = SomeJsonWriter(z)

  trait StrictMode0 {
    implicit def conflictingFallback1[A]: NoJsonWriter[A] = NoJsonWriter()
    implicit def conflictingFallback2[A]: NoJsonWriter[A] = NoJsonWriter()
  }
  object StrictMode extends StrictMode0 {
    implicit def lift[A](using z: JsonWriter[A]): SomeJsonWriter[A] = SomeJsonWriter(z)
  }
}
