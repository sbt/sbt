package test

import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)

trait NotAProject extends Project
abstract class AnotherNonProject extends Project
object YetAnotherNonProject extends DefaultProject(error("Shouldn't be called"))