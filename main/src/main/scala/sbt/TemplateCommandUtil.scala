/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.io.File

import sbt.SlashSyntax0._
import sbt.io._, syntax._
import sbt.util._
import sbt.internal.util.{ ConsoleAppender, Terminal => ITerminal }
import sbt.internal.util.complete.{ DefaultParsers, Parser }, DefaultParsers._
import xsbti.AppConfiguration
import sbt.librarymanagement._
import sbt.librarymanagement.ivy.{ IvyConfiguration, IvyDependencyResolution }
import sbt.internal.inc.classpath.ClasspathUtil
import BasicCommandStrings._, BasicKeys._

private[sbt] object TemplateCommandUtil {
  def templateCommand: Command = templateCommand0(TemplateCommand)
  def templateCommandAlias: Command = templateCommand0("init")
  private def templateCommand0(command: String): Command =
    Command(command, templateBrief, templateDetailed)(_ => templateCommandParser)(
      runTemplate
    )

  private def templateCommandParser: Parser[Seq[String]] =
    (token(Space) ~> repsep(StringBasic, token(Space))) | (token(EOF) map (_ => Nil))

  private def runTemplate(s0: State, inputArg: Seq[String]): State = {
    import BuildPaths._
    val globalBase = getGlobalBase(s0)
    val infos = (s0 get templateResolverInfos getOrElse Nil).toList
    val log = s0.globalLogging.full
    val extracted = (Project extract s0)
    val (s1, ivyConf) = extracted.runTask(Keys.ivyConfiguration, s0)
    val scalaModuleInfo = extracted.get(Keys.updateSbtClassifiers / Keys.scalaModuleInfo)
    val templateDescriptions = extracted.get(Keys.templateDescriptions)
    val args0 = inputArg.toList ++
      (s0.remainingCommands match {
        case exec :: Nil if exec.commandLine == "shell" => Nil
        case xs                                         => xs map (_.commandLine)
      })
    def terminate = TerminateAction :: s1.copy(remainingCommands = Nil)
    def reload = "reboot" :: s1.copy(remainingCommands = Nil)
    if (args0.nonEmpty) {
      run(infos, args0, s0.configuration, ivyConf, globalBase, scalaModuleInfo, log)
      terminate
    } else {
      fortifyArgs(templateDescriptions.toList) match {
        case Nil => terminate
        case arg :: Nil if arg.endsWith(".local") =>
          extracted.runInputTask(Keys.templateRunLocal, " " + arg, s0)
          reload
        case args =>
          run(infos, args, s0.configuration, ivyConf, globalBase, scalaModuleInfo, log)
          terminate
      }
    }
  }

  private def run(
      infos: List[TemplateResolverInfo],
      arguments: List[String],
      config: AppConfiguration,
      ivyConf: IvyConfiguration,
      globalBase: File,
      scalaModuleInfo: Option[ScalaModuleInfo],
      log: Logger
  ): Unit =
    infos find { info =>
      val loader = infoLoader(info, config, ivyConf, globalBase, scalaModuleInfo, log)
      val hit = tryTemplate(info, arguments, loader)
      if (hit) {
        runTemplate(info, arguments, loader)
      }
      hit
    } match {
      case Some(_) => // do nothing
      case None    => System.err.println("Template not found for: " + arguments.mkString(" "))
    }

  private def tryTemplate(
      info: TemplateResolverInfo,
      arguments: List[String],
      loader: ClassLoader
  ): Boolean = {
    val resultObj = call(info.implementationClass, "isDefined", loader)(
      classOf[Array[String]]
    )(arguments.toArray)
    resultObj.asInstanceOf[Boolean]
  }

  private def runTemplate(
      info: TemplateResolverInfo,
      arguments: List[String],
      loader: ClassLoader
  ): Unit = {
    call(info.implementationClass, "run", loader)(classOf[Array[String]])(arguments.toArray)
    ()
  }

  private def infoLoader(
      info: TemplateResolverInfo,
      config: AppConfiguration,
      ivyConf: IvyConfiguration,
      globalBase: File,
      scalaModuleInfo: Option[ScalaModuleInfo],
      log: Logger
  ): ClassLoader = {
    val cp = classpathForInfo(info, ivyConf, globalBase, scalaModuleInfo, log)
    ClasspathUtil.toLoader(cp, config.provider.loader)
  }

  private def call(
      interfaceClassName: String,
      methodName: String,
      loader: ClassLoader
  )(argTypes: Class[_]*)(args: AnyRef*): AnyRef = {
    val interfaceClass = getInterfaceClass(interfaceClassName, loader)
    val interface = interfaceClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
    val method = interfaceClass.getMethod(methodName, argTypes: _*)
    try {
      method.invoke(interface, args: _*)
    } catch {
      case e: InvocationTargetException => throw e.getCause
    }
  }

  private def getInterfaceClass(name: String, loader: ClassLoader) =
    Class.forName(name, true, loader)

  // Cache files under ~/.sbt/0.13/templates/org_name_version
  private def classpathForInfo(
      info: TemplateResolverInfo,
      ivyConf: IvyConfiguration,
      globalBase: File,
      scalaModuleInfo: Option[ScalaModuleInfo],
      log: Logger
  ): List[Path] = {
    val lm = IvyDependencyResolution(ivyConf)
    val templatesBaseDirectory = new File(globalBase, "templates")
    val templateId = s"${info.module.organization}_${info.module.name}_${info.module.revision}"
    val templateDirectory = new File(templatesBaseDirectory, templateId)
    def jars = (templateDirectory ** -DirectoryFilter).get
    if (!(info.module.revision endsWith "-SNAPSHOT") && jars.nonEmpty) jars.toList.map(_.toPath)
    else {
      IO.createDirectory(templateDirectory)
      val m = lm.wrapDependencyInModule(info.module, scalaModuleInfo)
      val xs = lm.retrieve(m, templateDirectory, log) match {
        case Left(_)      => sys.error(s"Retrieval of ${info.module} failed.")
        case Right(files) => files.toList
      }
      xs.map(_.toPath)
    }
  }

  private final val ScalaToolkitSlug = "scala/toolkit.local"
  private final val TypelevelToolkitSlug = "typelevel/toolkit.local"
  private final val SbtCrossPlatformSlug = "sbt/cross-platform.local"
  private lazy val term: ITerminal = ITerminal.get
  private lazy val isAnsiSupported = term.isAnsiSupported
  private lazy val nonMoveLetters = ('a' to 'z').toList diff List('h', 'j', 'k', 'l', 'q')
  private[sbt] lazy val defaultTemplateDescriptions = List(
    ScalaToolkitSlug -> "Scala Toolkit (beta) by Scala Center and VirtusLab",
    TypelevelToolkitSlug -> "Toolkit to start building Typelevel apps",
    SbtCrossPlatformSlug -> "A cross-JVM/JS/Native project",
    "scala/scala3.g8" -> "Scala 3 seed template",
    "scala/scala-seed.g8" -> "Scala 2 seed template",
    "playframework/play-scala-seed.g8" -> "A Play project in Scala",
    "playframework/play-java-seed.g8" -> "A Play project in Java",
    "softwaremill/tapir.g8" -> "A tapir project using Netty",
    "scala-js/vite.g8" -> "A Scala.JS + Vite project",
    "holdenk/sparkProjectTemplate.g8" -> "A Scala Spark project",
    "spotify/scio.g8" -> "A Scio project",
    "disneystreaming/smithy4s.g8" -> "A Smithy4s project",
  )
  private def fortifyArgs(templates: List[(String, String)]): List[String] =
    if (System.console eq null) Nil
    else
      ITerminal.withStreams(true, false) {
        assert(templates.size <= 20, "template list cannot have more than 20 items")
        val mappingList = templates.zipWithIndex.map {
          case (v, idx) => toLetter(idx) -> v
        }
        val out = term.printStream
        out.println("")
        out.println("Welcome to sbt new!")
        out.println("Here are some templates to get started:")
        val ans = askTemplate(mappingList, 0)
        val mappings = Map(mappingList: _*)
        mappings.get(ans).map(_._1).toList
      }

  private def toLetter(idx: Int): String =
    nonMoveLetters(idx).toString

  private def askTemplate(mappingList: List[(String, (String, String))], focus: Int): String = {
    val msg = "Select a template"
    displayMappings(mappingList, focus)
    val focusValue = toLetter(focus)
    if (!isAnsiSupported) ask(msg, focusValue)
    else {
      val out = term.printStream
      out.print(s"$msg: ")
      val ans0 = term.readArrow
      def printThenReturn(ans: String): String = {
        out.println(ans) // this is necessary to move the cursor
        out.flush()
        ans
      }
      ans0 match {
        case '\r' | '\n'    => printThenReturn(focusValue)
        case 'q' | 'Q' | -1 => printThenReturn("")
        case 'j' | 'J' | ITerminal.VK_DOWN =>
          clearMenu(mappingList)
          askTemplate(mappingList, math.min(focus + 1, mappingList.size - 1))
        case 'k' | 'K' | ITerminal.VK_UP =>
          clearMenu(mappingList)
          askTemplate(mappingList, math.max(focus - 1, 0))
        case c if nonMoveLetters.contains(c.toChar) =>
          printThenReturn(c.toChar.toString)
        case _ =>
          clearMenu(mappingList)
          askTemplate(mappingList, focus)
      }
    }
  }

  private def clearMenu(mappingList: List[(String, (String, String))]): Unit = {
    val out = term.printStream
    out.print(ConsoleAppender.CursorLeft1000)
    out.print(ConsoleAppender.cursorUp(mappingList.size + 1))
  }

  private def displayMappings(mappingList: List[(String, (String, String))], focus: Int): Unit = {
    import scala.Console.{ RESET, REVERSED }
    val out = term.printStream
    mappingList.zipWithIndex.foreach {
      case ((k, (slug, desc)), idx) =>
        if (idx == focus && isAnsiSupported) {
          out.print(REVERSED)
        }
        out.print(s" $k) ${slug.padTo(33, ' ')} - $desc")
        if (idx == focus && isAnsiSupported) {
          out.print(RESET)
        }
        out.println()
    }
    out.println(" q) quit")
    out.flush()
  }

  private def ask(question: String, default: String): String = {
    System.out.print(s"$question (default: $default): ")
    val ans0 = System.console.readLine()
    if (ans0 == "") default
    else ans0
  }

  // This is used by Defaults.runLocalTemplate, which implements
  // templateRunLocal input task.
  private[sbt] def defaultRunLocalTemplate(
      arguments: List[String],
      log: Logger
  ): Unit =
    arguments match {
      case ScalaToolkitSlug :: Nil     => scalaToolkitTemplate()
      case TypelevelToolkitSlug :: Nil => typelevelToolkitTemplate()
      case SbtCrossPlatformSlug :: Nil => sbtCrossPlatformTemplate()
      case _ =>
        System.err.println("Local template not found for: " + arguments.mkString(" "))
    }

  private final val defaultScalaV = "3.3.4"
  private def scalaToolkitTemplate(): Unit = {
    val defaultScalaToolkitV = "0.5.0"
    val scalaV = ask("Scala version", defaultScalaV)
    val toolkitV = ask("Scala Toolkit version", defaultScalaToolkitV)
    val content = s"""
val toolkitV = "$toolkitV"
val toolkit = "org.scala-lang" %% "toolkit" % toolkitV
val toolkitTest = "org.scala-lang" %% "toolkit-test" % toolkitV

ThisBuild / scalaVersion := "$scalaV"
libraryDependencies += toolkit
libraryDependencies += (toolkitTest % Test)
"""
    IO.write(new File("build.sbt"), content)
    copyResource("ScalaMain.scala.txt", new File("src/main/scala/example/Main.scala"))
    copyResource("MUnitSuite.scala.txt", new File("src/test/scala/example/ExampleSuite.scala"))
  }

  private def typelevelToolkitTemplate(): Unit = {
    val defaultTypelevelToolkitV = "0.1.28"
    val scalaV = ask("Scala version", defaultScalaV)
    val toolkitV = ask("Typelevel Toolkit version", defaultTypelevelToolkitV)
    val content = s"""
val toolkitV = "$toolkitV"
val toolkit = "org.typelevel" %% "toolkit" % toolkitV
val toolkitTest = "org.typelevel" %% "toolkit-test" % toolkitV

ThisBuild / scalaVersion := "$scalaV"
libraryDependencies += toolkit
libraryDependencies += (toolkitTest % Test)
"""
    IO.write(new File("build.sbt"), content)
    copyResource("TypelevelMain.scala.txt", new File("src/main/scala/example/Main.scala"))
    copyResource(
      "TypelevelExampleSuite.scala.txt",
      new File("src/test/scala/example/ExampleSuite.scala")
    )
  }

  private def sbtCrossPlatformTemplate(): Unit = {
    val scalaV = ask("Scala version", defaultScalaV)
    val content = s"""
ThisBuild / scalaVersion := "$scalaV"

lazy val core = (projectMatrix in file("core"))
  .settings(
    name := "core",
  )
  .jvmPlatform(scalaVersions = Seq("$scalaV"))
  .jsPlatform(scalaVersions = Seq("$scalaV"))
  .nativePlatform(scalaVersions = Seq("$scalaV"))
"""
    IO.write(new File("build.sbt"), content)

    val pluginsContent = """
addSbtPlugin("com.eed3si9n" % "sbt-projectmatrix" % "0.10.0")
addSbtPlugin("org.scala-js" % "sbt-scalajs" % "1.17.0")
addSbtPlugin("org.scala-native" % "sbt-scala-native" % "0.5.5")
"""
    IO.write(new File("project/plugins.sbt"), pluginsContent)
    copyResource("ScalaMain.scala.txt", new File("core/src/main/scala/example/Main.scala"))
  }

  private def copyResource(resourcePath: String, out: File): Unit = {
    if (out.exists()) {
      sys.error(s"the file $out already exists!")
    }
    if (!out.getParentFile().exists()) {
      IO.createDirectory(out.getParentFile())
    }
    val is = getClass.getClassLoader().getResourceAsStream(resourcePath)
    require(is ne null, s"Couldn't load '$resourcePath' from classpath.")
    try {
      IO.transfer(is, out)
    } finally {
      is.close()
    }
  }
}
