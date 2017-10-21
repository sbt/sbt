import scala.util.control.NonFatal
import sbt._
import Keys._

import sbt.internal.inc.Analysis

object Util {
  val ExclusiveTest: Tags.Tag = Tags.Tag("exclusive-test")

  val componentID: SettingKey[Option[String]] = settingKey[Option[String]]("")
  val scalaKeywords: TaskKey[Set[String]] = taskKey[Set[String]]("")
  val generateKeywords: TaskKey[File] = taskKey[File]("")

  def noPublishSettings: Seq[Setting[_]] = Seq(publish := {})

  def crossBuild: Seq[Setting[_]] =
    Seq(
      crossPaths := (scalaBinaryVersion.value match {
        case "2.10" => false
        case _      => true
      })
    )

  lazy val javaOnlySettings: Seq[Setting[_]] = Seq(
    // crossPaths := false,
    compileOrder := CompileOrder.JavaThenScala,
    unmanagedSourceDirectories in Compile := Seq((javaSource in Compile).value)
  )

  lazy val baseScalacOptions = Seq(
    scalacOptions ++= Seq("-Xelide-below", "0"),
    scalacOptions ++= (CrossVersion partialVersion scalaVersion.value match {
      case Some((2, 9)) | Some((2, 8)) =>
        Nil // support 2.9 for some subprojects for the Scala Eclipse IDE
      case _ =>
        Seq(
          "-encoding",
          "utf8",
          "-deprecation",
          "-feature",
          "-unchecked",
          "-Xlint",
          "-language:existentials",
          "-language:higherKinds",
          "-language:implicitConversions",
          "-language:postfixOps",
          "-Xfuture",
          "-Yno-adapted-args",
          "-Ywarn-dead-code",
          "-Ywarn-numeric-widen",
          "-Ywarn-unused:-patvars,-params,-implicits,_",
          "-Ywarn-unused-import"
        )
    }),
    scalacOptions ++= (CrossVersion partialVersion scalaVersion.value match {
      case Some((2, 10)) => Seq("-deprecation", "-Xlint")
      case _             => Seq()
    })
  )

  def projectComponent: Setting[_] =
    projectID := (componentID.value match {
      case Some(id) => projectID.value extra ("e:component" -> id)
      case None     => projectID.value
    })

  lazy val apiDefinitions = TaskKey[Seq[File]]("api-definitions")

  def generateAPICached(defs: Seq[File],
                        cp: Classpath,
                        out: File,
                        main: Option[String],
                        run: ScalaRun,
                        s: TaskStreams): Seq[File] = {
    def gen() = generateAPI(defs, cp, out, main, run, s)
    val f = FileFunction.cached(s.cacheDirectory / "gen-api", FilesInfo.hash) { _ =>
      gen().toSet
    } // TODO: check if output directory changed
    f(defs.toSet).toSeq
  }
  def generateAPI(defs: Seq[File],
                  cp: Classpath,
                  out: File,
                  main: Option[String],
                  run: ScalaRun,
                  s: TaskStreams): Seq[File] = {
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
  def generateVersionFile(version: String,
                          dir: File,
                          s: TaskStreams,
                          analysis: Analysis): Seq[File] = {
    import java.util.{ Date, TimeZone }
    val formatter = new java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss")
    formatter.setTimeZone(TimeZone.getTimeZone("GMT"))
    val timestamp = formatter.format(new Date)
    val content = versionLine(version) + "\ntimestamp=" + timestamp
    val f = dir / "xsbt.version.properties"
    if (!f.exists || f.lastModified < lastCompilationTime(analysis) || !containsVersion(f, version)) {
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
    import scala.xml._
    def cleanNodes(nodes: Seq[Node]): Seq[Node] = nodes flatMap {
      case elem @ Elem(prefix, "dependency", attributes, scope, children @ _*)
          if excludePomDependency(elem) =>
        NodeSeq.Empty
      case Elem(prefix, "classifier", attributes, scope, children @ _*) =>
        NodeSeq.Empty
      case Elem(prefix, label, attributes, scope, children @ _*) =>
        val cleanedNodes = cleanNodes(children)
        Elem(prefix, label, attributes, scope, cleanedNodes.isEmpty, cleanedNodes: _*).theSeq
      case other => other
    }
    cleanNodes(pomNode.theSeq)(0)
  }

  def excludePomDependency(node: scala.xml.Node) = node \ "artifactId" exists { n =>
    excludePomArtifact(n.text)
  }

  def excludePomArtifact(artifactId: String) = (artifactId startsWith "compiler-bridge")

  val testExclusive = tags in test += ((ExclusiveTest, 1))

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
    unmanagedResources in Compile ++= notice.value +: extractLicenses.value,
    extractLicenses := extractLicenses0((baseDirectory in ThisBuild).value,
                                        notice.value,
                                        streams.value)
  )
  def extractLicenses0(base: File, note: File, s: TaskStreams): Seq[File] =
    if (!note.exists) Nil
    else
      try { seePaths(base, IO.read(note)) } catch {
        case NonFatal(e) => s.log.warn("Could not read NOTICE"); Nil
      }
}
