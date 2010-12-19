package sbt

	import std._

trait TestProject extends Project with ReflectiveProject with ProjectConstructors with LastOutput with PrintTask with ProjectExtra with Exec with Javap