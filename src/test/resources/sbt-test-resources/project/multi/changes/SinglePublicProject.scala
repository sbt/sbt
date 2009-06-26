package test

import sbt._

class TestProject(info: ProjectInfo) extends DefaultProject(info)
protected class NotMainProject(info: ProjectInfo) extends DefaultProject(info)
private class AnotherNotMainProject(info: ProjectInfo) extends DefaultProject(info)