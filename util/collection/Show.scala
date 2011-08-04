package sbt

trait Show[T] {
	def apply(t: T): String
}