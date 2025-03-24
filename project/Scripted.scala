package local

import java.lang.reflect.InvocationTargetException

import sbt.*
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath.{ ClasspathUtilities, FilteredLoader }
import scala.annotation.nowarn

object LocalScriptedPlugin extends AutoPlugin {
  override def requires = plugins.JvmPlugin

  object autoImport extends ScriptedKeys
}

trait ScriptedKeys {
  val publishLocalBinAll = taskKey[Unit]("")
  val scriptedUnpublished = inputKey[Unit](
    "Execute scripted without publishing sbt first. " +
      "Saves you some time when only your test has changed"
  )
  val scriptedSource = settingKey[File]("")
  val scriptedPrescripted = taskKey[File => Unit]("")
}

object Scripted {
  // This is to workaround https://github.com/sbt/io/issues/110
  sys.props.put("jna.nosys", "true")

  val RepoOverrideTest = config("repoOverrideTest") extend Compile

  import sbt.complete.*

  // Paging, 1-index based.
  final case class ScriptedTestPage(page: Int, total: Int)

  // FIXME: Duplicated with ScriptedPlugin.scriptedParser, this can be
  // avoided once we upgrade build.properties to 0.13.14
  def scriptedParser(scriptedBase: File): Parser[Seq[String]] = {
    import DefaultParsers.*

    val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map {
      (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
    }
    val pairMap = pairs.groupBy(_._1).mapValues(_.map(_._2).toSet)

    val id = charClass(c => !c.isWhitespace && c != '/').+.string
    val groupP = token(id.examples(pairMap.keySet)) <~ token('/')

    // A parser for page definitions
    val pageNumber = (NatBasic & not('0', "zero page number")).flatMap { i =>
      if (i <= pairs.size) Parser.success(i)
      else Parser.failure(s"$i exceeds the number of tests (${pairs.size})")
    }
    val pageP: Parser[ScriptedTestPage] = ("*" ~> pageNumber ~ ("of" ~> pageNumber)) flatMap {
      case (page, total) if page <= total => success(ScriptedTestPage(page, total))
      case (page, total)                  => failure(s"Page $page was greater than $total")
    }

    // Grabs the filenames from a given test group in the current page definition.
    def pagedFilenames(group: String, page: ScriptedTestPage): Seq[String] = {
      val files = pairMap.get(group).toSeq.flatten.sortBy(_.toLowerCase)
      val pageSize = if (page.total == 0) files.size else files.size / page.total
      // The last page may loose some values, so we explicitly keep them
      val dropped = files.drop(pageSize * (page.page - 1))
      if (page.page == page.total) dropped
      else dropped.take(pageSize)
    }

    def nameP(group: String) = {
      token("*".id | id.examples(pairMap.getOrElse(group, Set.empty[String])))
    }

    val PagedIds: Parser[Seq[String]] =
      for {
        group <- groupP
        page <- pageP
        files = pagedFilenames(group, page)
        // TODO -  Fail the parser if we don't have enough files for the given page size
        // if !files.isEmpty
      } yield files map (f => s"$group/$f")

    val testID = (for (group <- groupP; name <- nameP(group)) yield (group, name))
    val testIdAsGroup = matched(testID) map (test => Seq(test))

    // (token(Space) ~> matched(testID)).*
    (token(Space) ~> (PagedIds | testIdAsGroup)).* map (_.flatten)
  }

  @nowarn
  def doScripted(
      scriptedSbtInstance: ScalaInstance,
      sourcePath: File,
      bufferLog: Boolean,
      args: Seq[String],
      prescripted: File => Unit,
      launchOpts: Seq[String],
      scalaVersion: String,
      sbtVersion: String,
      classpath: Seq[File],
      launcherJar: File,
      logger: Logger
  ): Unit = {
    logger.info(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")
    logger.info("")

    // Force Log4J to not use a thread context classloader otherwise it throws a CCE
    sys.props(org.apache.logging.log4j.util.LoaderUtil.IGNORE_TCCL_PROPERTY) = "true"

    val noJLine = new FilteredLoader(scriptedSbtInstance.loader, "jline." :: Nil)
    val loader = ClasspathUtilities.toLoader(classpath, noJLine)
    val bridgeClass = Class.forName("sbt.scriptedtest.ScriptedRunner", true, loader)

    // Interface to cross class loader
    type SbtScriptedRunner = {
      // def runInParallel(
      //     resourceBaseDirectory: File,
      //     bufferLog: Boolean,
      //     tests: Array[String],
      //     launchOpts: Array[String],
      //     prescripted: java.util.List[File],
      //     scalaVersion: String,
      //     sbtVersion: String,
      //     classpath: Array[File],
      //     instances: Int
      // ): Unit

      def runInParallel(
          resourceBaseDirectory: File,
          bufferLog: Boolean,
          tests: Array[String],
          launcherJar: File,
          javaCommand: String,
          launchOpts: Array[String],
          prescripted: java.util.List[File],
          instance: Int,
      ): Unit
    }

    val initLoader = Thread.currentThread.getContextClassLoader
    try {
      Thread.currentThread.setContextClassLoader(loader)
      val bridge =
        bridgeClass.getDeclaredConstructor().newInstance().asInstanceOf[SbtScriptedRunner]
      try {
        // Using java.util.List to encode File => Unit.
        val callback = new java.util.AbstractList[File] {
          override def add(x: File): Boolean = { prescripted(x); false }
          def get(x: Int): sbt.File = ???
          def size(): Int = 0
        }
        val instances: Int = (System.getProperty("sbt.scripted.parallel.instances") match {
          case null => 1
          case i    => scala.util.Try(i.toInt).getOrElse(1)
        }) match {
          case i if i > 0 => i
          case _          => 1
        }
        import scala.language.reflectiveCalls

        // bridge.runInParallel(
        //   sourcePath,
        //   bufferLog,
        //   args.toArray,
        //   launchOpts.toArray,
        //   callback,
        //   scalaVersion,
        //   sbtVersion,
        //   classpath.toArray,
        //   instances
        // )
        bridge.runInParallel(
          sourcePath,
          bufferLog,
          args.toArray,
          launcherJar,
          "java",
          launchOpts.toArray,
          callback,
          instances
        )
      } catch { case ite: InvocationTargetException => throw ite.getCause }
    } finally {
      Thread.currentThread.setContextClassLoader(initLoader)
    }
  }
}
