import scala.util.control.NonFatal
import sbt.*
import Keys.*
import scalafix.sbt.ScalafixPlugin.autoImport.scalafix

import sbt.internal.inc.Analysis

object Utils {
  val version2_13 = settingKey[String]("version number")
  val ExclusiveTest: Tags.Tag = Tags.Tag("exclusive-test")

  val componentID: SettingKey[Option[String]] = settingKey[Option[String]]("")
  val scalaKeywords: TaskKey[Set[String]] = taskKey[Set[String]]("")
  val generateKeywords: TaskKey[File] = taskKey[File]("")

  lazy val noPublish = Seq(
    publish := {},
    publish / skip := true,
  )

  def crossBuild: Seq[Setting[_]] =
    Seq(
      crossPaths := true
    )

  lazy val javaOnlySettings: Seq[Setting[_]] = Seq(
    // crossPaths := false,
    // compileOrder := CompileOrder.JavaThenScala,
    Compile / unmanagedSourceDirectories := Seq((Compile / javaSource).value)
  )

  lazy val baseScalacOptions = Seq(
    Compile / doc / scalacOptions -= "-Xlint",
    Compile / doc / scalacOptions -= "-Xfatal-warnings",
  )

  def projectComponent: Setting[?] =
    projectID := (componentID.value match {
      case Some(id) => projectID.value extra ("e:component" -> id)
      case None     => projectID.value
    })

  lazy val apiDefinitions = TaskKey[Seq[File]]("api-definitions")

  def generateAPICached(
      defs: Seq[File],
      cp: Classpath,
      out: File,
      main: Option[String],
      run: ScalaRun,
      s: TaskStreams
  ): Seq[File] = {
    def gen() = generateAPI(defs, cp, out, main, run, s)
    val f = FileFunction.cached(s.cacheDirectory / "gen-api", FilesInfo.hash) { _ =>
      gen().toSet
    } // TODO: check if output directory changed
    f(defs.toSet).toSeq
  }
  def generateAPI(
      defs: Seq[File],
      cp: Classpath,
      out: File,
      main: Option[String],
      run: ScalaRun,
      s: TaskStreams
  ): Seq[File] = {
    IO.delete(out)
    IO.createDirectory(out)
    val args = "xsbti.api" :: out.getAbsolutePath :: defs.map(_.getAbsolutePath).toList
    val mainClass = main getOrElse "No main class defined for datatype generator"
    run.run(mainClass, cp.files, args, s.log).failed foreach (e => sys error e.getMessage)
    (out ** "*.java").get
  }
  def lastCompilationTime(analysis: Analysis): Long = {
    val lastCompilation = analysis.compilations.allCompilations.lastOption
    lastCompilation.map(_.getStartTime) getOrElse 0L
  }
  def generateVersionFile(
      version: String,
      dir: File,
      s: TaskStreams,
      analysis: Analysis
  ): Seq[File] = {
    import java.util.{ Date, TimeZone }
    val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
    val timestamp = formatter.format(new Date)
    val content = versionLine(version) + "\ntimestamp=" + timestamp
    val f = dir / "xsbt.version.properties"
    // TODO: replace lastModified() with sbt.io.IO.getModifiedTimeOrZero(), once the build
    // has been upgraded to a version of sbt that includes that call.
    if (
      !f.exists || f.lastModified < lastCompilationTime(analysis) || !containsVersion(f, version)
    ) {
      s.log.info("Writing version information to " + f + " :\n" + content)
      IO.write(f, content)
    }
    f :: Nil
  }
  def versionLine(version: String): String = "version=" + version
  def containsVersion(propFile: File, version: String): Boolean =
    IO.read(propFile).contains(versionLine(version))

  def binID = "compiler-interface-bin"
  def srcID = "compiler-interface-src"

  def publishPomSettings: Seq[Setting[_]] = Seq(
    pomPostProcess := cleanPom _
  )

  def cleanPom(pomNode: scala.xml.Node) = {
    import scala.xml.*
    def cleanNodes(nodes: Seq[Node]): Seq[Node] = nodes flatMap {
      case elem @ Elem(_, "dependency", _, _, _*) if excludePomDependency(elem) =>
        NodeSeq.Empty
      case Elem(_, "classifier", _, _, _*) =>
        NodeSeq.Empty
      case Elem(prefix, label, attributes, scope, children*) =>
        val cleanedNodes = cleanNodes(children)
        Elem(prefix, label, attributes, scope, cleanedNodes.isEmpty, cleanedNodes*).theSeq
      case other => other
    }
    cleanNodes(pomNode.theSeq)(0)
  }

  def excludePomDependency(node: scala.xml.Node) = node \ "artifactId" exists { n =>
    excludePomArtifact(n.text)
  }

  def excludePomArtifact(artifactId: String) = (artifactId startsWith "compiler-bridge")

  val testExclusive = test / tags += (ExclusiveTest, 1)

  // TODO: replace with Tags.exclusive after 0.12.0
  val testExclusiveRestriction = Tags.customLimit { (tags: Map[Tags.Tag, Int]) =>
    val exclusive = tags.getOrElse(ExclusiveTest, 0)
    val all = tags.getOrElse(Tags.All, 0)
    exclusive == 0 || all == 1
  }

  def getScalaKeywords: Set[String] = {
    val g = new scala.tools.nsc.Global(new scala.tools.nsc.Settings)
    g.nme.keywords.map(_.toString)
  }

  def writeScalaKeywords(base: File, keywords: Set[String]): File = {
    val init = keywords.map(tn => '"' + tn + '"').mkString("Set(", ", ", ")")
    val ObjectName = "ScalaKeywords"
    val PackageName = "sbt.internal.util"
    val keywordsSrc = s"""
      |package $PackageName
      |object $ObjectName {
      |  val values = $init
      |}
    """.trim.stripMargin
    val out = base / PackageName.replace('.', '/') / s"$ObjectName.scala"
    IO.write(out, keywordsSrc)
    out
  }

  def keywordsSettings: Seq[Setting[_]] =
    inConfig(Compile)(
      Seq(
        scalaKeywords := getScalaKeywords,
        generateKeywords := writeScalaKeywords(sourceManaged.value, scalaKeywords.value),
        sourceGenerators += Def.task(Seq(generateKeywords.value)).taskValue
      )
    )

  def dataclassGen(data: Reference) = Def.taskDyn {
    val root = (ThisBuild / baseDirectory).value.toURI.toString
    val from = (data / Compile / sourceDirectory).value
    val to = (Compile / sourceManaged).value
    val outFrom = from.toURI.toString.stripSuffix("/").stripPrefix(root)
    val outTo = to.toURI.toString.stripSuffix("/").stripPrefix(root)
    val _ = (data / Compile / compile).value
    Def.task {
      val _ = (data / Compile / scalafix)
        .toTask(s" --rules GenerateDataClass --out-from=$outFrom --out-to=$outTo")
        .value
      (to ** "*.scala").get
    }
  }
}

object Licensed {
  lazy val notice = SettingKey[File]("notice")
  lazy val extractLicenses = TaskKey[Seq[File]]("extract-licenses")

  lazy val seeRegex = """\(see (.*?)\)""".r
  def licensePath(base: File, str: String): File = {
    val path = base / str;
    if (path.exists) path else sys.error("Referenced license '" + str + "' not found at " + path)
  }
  def seePaths(base: File, noticeString: String): Seq[File] =
    seeRegex.findAllIn(noticeString).matchData.map(d => licensePath(base, d.group(1))).toList

  def settings: Seq[Setting[_]] = Seq(
    notice := (baseDirectory.value / "NOTICE"),
    Compile / unmanagedResources ++= notice.value +: extractLicenses.value,
    extractLicenses := extractLicenses0(
      (ThisBuild / baseDirectory).value,
      notice.value,
      streams.value
    )
  )
  def extractLicenses0(base: File, note: File, s: TaskStreams): Seq[File] =
    if (!note.exists) Nil
    else
      try {
        seePaths(base, IO.read(note))
      } catch {
        case NonFatal(_) => s.log.warn("Could not read NOTICE"); Nil
      }
}
