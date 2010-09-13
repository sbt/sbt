package sbt

	import std._

trait TestProject extends Project with ReflectiveProject with ProjectConstructors with TaskExtra with Types with LastOutput with PrintTask