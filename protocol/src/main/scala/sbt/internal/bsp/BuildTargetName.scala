package sbt.internal.bsp

object BuildTargetName {
  def fromScope(project: String, config: String): String = {
    config match {
      case "compile" => project
      case _         => s"$project-$config"
    }
  }
}
