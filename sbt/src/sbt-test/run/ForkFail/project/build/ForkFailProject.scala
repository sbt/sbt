import sbt._

class ForkFailProject(info: ProjectInfo) extends DefaultProject(info) {
  override def fork = forkRun
}

