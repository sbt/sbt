import sbt.*
import sbt.Keys.*

object Transform {

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
