import sbt._
import Keys._

object Transform {
  lazy val transformSources = TaskKey[Seq[File]]("transform-sources")
  lazy val inputSourceDirectories = SettingKey[Seq[File]]("input-source-directories")
  lazy val inputSourceDirectory = SettingKey[File]("input-source-directory")
  lazy val inputSources = TaskKey[Seq[File]]("input-sources")
  lazy val sourceProperties = TaskKey[Map[String, String]]("source-properties")

  lazy val transformResources = TaskKey[Seq[File]]("transform-resources")
  lazy val inputResourceDirectories = SettingKey[Seq[File]]("input-resource-directories")
  lazy val inputResourceDirectory = SettingKey[File]("input-resource-directory")
  lazy val inputResources = TaskKey[Seq[File]]("input-resources")
  lazy val resourceProperties = TaskKey[Map[String, String]]("resource-properties")

  lazy val conscriptConfigs = TaskKey[Unit]("conscript-configs")

  def conscriptSettings(launch: Reference) = Seq(
    conscriptConfigs <<= (managedResources in launch in Compile, sourceDirectory in Compile).map { (res, src) =>
      val source = res.find(_.getName == "sbt.boot.properties") getOrElse sys.error("No managed boot.properties file.")
      copyConscriptProperties(source, src / "conscript")
      ()
    }
  )
  def copyConscriptProperties(source: File, conscriptBase: File): Seq[File] =
    {
      IO.delete(conscriptBase)
      val pairs = Seq(
        "sbt.xMain" -> "sbt",
        "sbt.ScriptMain" -> "scalas",
        "sbt.ConsoleMain" -> "screpl"
      )
      for ((main, dir) <- pairs) yield {
        val file = conscriptBase / dir / "launchconfig"
        copyPropertiesFile(source, main, file)
        file
      }
    }
  def copyPropertiesFile(source: File, newMain: String, target: File) {
    def subMain(line: String): String = if (line.trim.startsWith("class:")) "  class: " + newMain else line
    IO.writeLines(target, IO.readLines(source) map subMain)
  }

  def crossGenSettings = transSourceSettings ++ Seq(
    sourceProperties := Map("cross.package0" -> "sbt", "cross.package1" -> "cross")
  )
  def transSourceSettings = Seq(
    inputSourceDirectory := sourceDirectory.value / "input_sources",
    inputSourceDirectories <<= Seq(inputSourceDirectory).join,
    inputSources <<= inputSourceDirectories.map(dirs => (dirs ** (-DirectoryFilter)).get),
    fileMappings in transformSources <<= transformSourceMappings,
    transformSources <<= (fileMappings in transformSources, sourceProperties) map { (rs, props) =>
      rs map { case (in, out) => transform(in, out, props) }
    },
    sourceGenerators <+= transformSources
  )
  def transformSourceMappings = (inputSources, inputSourceDirectories, sourceManaged) map { (ss, sdirs, sm) =>
    ((ss --- sdirs) pair (rebase(sdirs, sm) | flat(sm))).toSeq
  }
  def configSettings = transResourceSettings ++ Seq(
    resourceProperties <<= (organization, version, scalaVersion, isSnapshot) map { (org, v, sv, isSnapshot) =>
      Map("org" -> org, "sbt.version" -> v, "scala.version" -> sv, "repositories" -> repositories(isSnapshot).mkString(IO.Newline))
    }
  )
  def transResourceSettings = Seq(
    inputResourceDirectory := sourceDirectory.value / "input_resources",
    inputResourceDirectories <<= Seq(inputResourceDirectory).join,
    inputResources <<= inputResourceDirectories.map(dirs => (dirs ** (-DirectoryFilter)).get),
    fileMappings in transformResources <<= transformResourceMappings,
    transformResources <<= (fileMappings in transformResources, resourceProperties) map { (rs, props) =>
      rs map { case (in, out) => transform(in, out, props) }
    },
    resourceGenerators <+= transformResources
  )
  def transformResourceMappings = (inputResources, inputResourceDirectories, resourceManaged) map { (rs, rdirs, rm) =>
    ((rs --- rdirs) pair (rebase(rdirs, rm) | flat(rm))).toSeq
  }

  def transform(in: File, out: File, map: Map[String, String]): File =
    {
      def get(key: String): String = map.getOrElse(key, sys.error("No value defined for key '" + key + "'"))
      val newString = Property.replaceAllIn(IO.read(in), mtch => get(mtch.group(1)))
      if (Some(newString) != read(out))
        IO.write(out, newString)
      out
    }
  def read(file: File): Option[String] = try { Some(IO.read(file)) } catch { case _: java.io.IOException => None }
  lazy val Property = """\$\{\{([\w.-]+)\}\}""".r

  def repositories(isSnapshot: Boolean) = Releases :: (if (isSnapshot) Snapshots :: SonatypeSnapshots :: Nil else Nil)
  lazy val Releases = typesafeRepository("releases")
  lazy val Snapshots = typesafeRepository("snapshots")
  lazy val SonatypeSnapshots = sonatypeRepsoitory("snapshots")
  def sonatypeRepsoitory(status: String) =
    s"""  sonatype-$status: https://oss.sonatype.org/content/repositories/$status"""
  def typesafeRepository(status: String) =
    """  typesafe-ivy-%s: https://repo.typesafe.com/typesafe/ivy-%<s/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext], bootOnly""" format status
}
