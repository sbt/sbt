import sbt._
import sbt.Keys._

object Transform {
  private val conscriptConfigs = taskKey[Unit]("")

  def conscriptSettings(launch: Reference) = Seq(
    conscriptConfigs := {
      val sourceFile = (launch / Compile / managedResources).value
        .find(_.getName == "sbt.boot.properties")
        .getOrElse(sys.error("No managed boot.properties file."))
      val source = IO.readLines(sourceFile)
      val conscriptBase = (Compile / sourceDirectory).value / "conscript"
      IO.delete(conscriptBase)
      val pairs = Seq(
        "sbt.xMain" -> "xsbt",
        "sbt.ScriptMain" -> "scalas",
        "sbt.ConsoleMain" -> "screpl",
      )
      for ((main, dir) <- pairs) {
        val lines = source.map(l => if (l.trim.startsWith("class:")) s"  class: $main" else l)
        IO.writeLines(conscriptBase / dir / "launchconfig", lines)
      }
    },
  )

  def configSettings = Seq(
    resourceGenerators += Def.task {
      val rdirs = Seq(sourceDirectory.value / "input_resources")
      val rm = resourceManaged.value
      val paths = (rdirs ** (-DirectoryFilter)).get --- rdirs
      val rs = paths.pair(Path.rebase(rdirs, rm) | Path.flat(rm))
      val props = Map(
        "org" -> organization.value,
        "sbt.version" -> version.value,
        "scala.version" -> scalaVersion.value,
      )
      def get(key: String) = props.getOrElse(key, sys.error(s"No value defined for key '$key'"))
      val Property = """\$\{\{([\w.-]+)\}\}""".r
      val catcher = scala.util.control.Exception.catching(classOf[java.io.IOException])
      rs.map { case (in, out) =>
        val newString = Property.replaceAllIn(IO.read(in), mtch => get(mtch.group(1)))
        if (Some(newString) != catcher.opt(IO.read(out)))
          IO.write(out, newString)
        out
      }
    }.taskValue,
  )
}
