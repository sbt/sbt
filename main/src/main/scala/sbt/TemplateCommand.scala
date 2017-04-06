package sbt

import java.lang.reflect.InvocationTargetException
import java.io.File

import sbt.io._, syntax._
import sbt.util._
import sbt.internal.util.complete.{ DefaultParsers, Parser }, DefaultParsers._
import xsbti.AppConfiguration
import sbt.librarymanagement._
import sbt.internal.librarymanagement.IvyConfiguration
import sbt.internal.inc.classpath.ClasspathUtilities
import BasicCommandStrings._, BasicKeys._

private[sbt] object TemplateCommandUtil {
  def templateCommand: Command =
    Command(TemplateCommand, templateBrief, templateDetailed)(templateCommandParser)(runTemplate)

  private def templateCommandParser(state: State): Parser[Seq[String]] =
    (token(Space) ~> repsep(StringBasic, token(Space))) | (token(EOF) map (_ => Nil))

  private def runTemplate(state: State, inputArg: Seq[String]): State = {
    val infos = (state get templateResolverInfos getOrElse Nil).toList
    val log = state.globalLogging.full
    val extracted = (Project extract state)
    val (s2, ivyConf) = extracted.runTask(Keys.ivyConfiguration, state)
    val globalBase = BuildPaths.getGlobalBase(state)
    val ivyScala = extracted.get(Keys.ivyScala in Keys.updateSbtClassifiers)
    val arguments = inputArg.toList ++
      (state.remainingCommands match {
        case exec :: Nil if exec.commandLine == "shell" => Nil
        case xs                                         => xs map (_.commandLine)
      })
    run(infos, arguments, state.configuration, ivyConf, globalBase, ivyScala, log)
    "exit" :: s2.copy(remainingCommands = Nil)
  }

  private def run(
      infos: List[TemplateResolverInfo], arguments: List[String], config: AppConfiguration,
      ivyConf: IvyConfiguration, globalBase: File, ivyScala: Option[IvyScala], log: Logger
  ): Unit =
    infos find { info =>
      val loader = infoLoader(info, config, ivyConf, globalBase, ivyScala, log)
      val hit = tryTemplate(info, arguments, loader)
      if (hit) {
        runTemplate(info, arguments, loader)
      }
      hit
    } match {
      case Some(_) => // do nothing
      case None    => System.err.println("Template not found for: " + arguments.mkString(" "))
    }

  private def tryTemplate(info: TemplateResolverInfo, arguments: List[String], loader: ClassLoader): Boolean =
    {
      val resultObj = call(info.implementationClass, "isDefined", loader)(
        classOf[Array[String]]
      )(arguments.toArray)
      resultObj.asInstanceOf[Boolean]
    }

  private def runTemplate(info: TemplateResolverInfo, arguments: List[String], loader: ClassLoader): Unit =
    call(info.implementationClass, "run", loader)(classOf[Array[String]])(arguments.toArray)

  private def infoLoader(
      info: TemplateResolverInfo, config: AppConfiguration, ivyConf: IvyConfiguration, globalBase: File,
      ivyScala: Option[IvyScala], log: Logger
  ): ClassLoader = {
    val cp = classpathForInfo(info, ivyConf, globalBase, ivyScala, log)
    ClasspathUtilities.toLoader(cp, config.provider.loader)
  }

  private def call(
      interfaceClassName: String, methodName: String, loader: ClassLoader
  )(argTypes: Class[_]*)(args: AnyRef*): AnyRef =
    {
      val interfaceClass = getInterfaceClass(interfaceClassName, loader)
      val interface = interfaceClass.getDeclaredConstructor().newInstance().asInstanceOf[AnyRef]
      val method = interfaceClass.getMethod(methodName, argTypes: _*)
      try { method.invoke(interface, args: _*) }
      catch {
        case e: InvocationTargetException => throw e.getCause
      }
    }

  private def getInterfaceClass(name: String, loader: ClassLoader) = Class.forName(name, true, loader)

  // Cache files under ~/.sbt/0.13/templates/org_name_version
  private def classpathForInfo(
      info: TemplateResolverInfo, ivyConf: IvyConfiguration, globalBase: File, ivyScala: Option[IvyScala],
      log: Logger
  ): List[File] =
    {
      val lm = new DefaultLibraryManagement(ivyConf, log)
      val templatesBaseDirectory = new File(globalBase, "templates")
      val templateId = s"${info.module.organization}_${info.module.name}_${info.module.revision}"
      val templateDirectory = new File(templatesBaseDirectory, templateId)
      def jars = (templateDirectory ** -DirectoryFilter).get
      if (!(info.module.revision endsWith "-SNAPSHOT") && jars.nonEmpty) jars.toList
      else {
        IO.createDirectory(templateDirectory)
        val m = lm.getModule(info.module.withConfigurations(Some("component")), ivyScala)
        val xs = lm.update(m, templateDirectory)(_ => true).toList.flatten
        xs
      }
    }
}
