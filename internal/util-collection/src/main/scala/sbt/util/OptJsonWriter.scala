package sbt.util

import sjsonnew.JsonWriter

sealed trait OptJsonWriter[A]
final case class NoJsonWriter[A]() extends OptJsonWriter[A]
final case class SomeJsonWriter[A](value: JsonWriter[A]) extends OptJsonWriter[A]

trait OptJsonWriter0 {
  implicit def fallback[A]: NoJsonWriter[A] = NoJsonWriter()
}
object OptJsonWriter extends OptJsonWriter0 {
  implicit def lift[A](implicit z: JsonWriter[A]): SomeJsonWriter[A] = SomeJsonWriter(z)
}
