/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import java.lang.reflect.InvocationTargetException
import java.nio.file.Path
import java.io.File

import sbt.BasicCommandStrings.TerminateAction
import sbt.SlashSyntax0._
import sbt.io._, syntax._
import sbt.util._
import sbt.internal.util.complete.{ DefaultParsers, Parser }, DefaultParsers._
import xsbti.AppConfiguration
import sbt.librarymanagement._
import sbt.librarymanagement.ivy.{ IvyConfiguration, IvyDependencyResolution }
import sbt.internal.inc.classpath.ClasspathUtil
import BasicCommandStrings._, BasicKeys._

private[sbt] object TemplateCommandUtil {
  def templateCommand: Command =
    Command(TemplateCommand, templateBrief, templateDetailed)(_ => templateCommandParser)(
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
    val arguments = inputArg.toList ++
      (s0.remainingCommands match {
        case exec :: Nil if exec.commandLine == "shell" => Nil
        case xs                                         => xs map (_.commandLine)
      })
    run(infos, arguments, s0.configuration, ivyConf, globalBase, scalaModuleInfo, log)
    TerminateAction :: s1.copy(remainingCommands = Nil)
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
}
