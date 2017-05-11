import sbt._
import Keys._
import Def.Initialize
import sbt.internal.inc.ScalaInstance
import sbt.internal.inc.classpath

import scala.language.reflectiveCalls

object ScriptedPlugin extends sbt.AutoPlugin {
  override def requires = plugins.JvmPlugin
  object autoImport extends ScriptedKeys {
    def scriptedPath = file("scripted")
  }

  import autoImport._
  import Scripted._
  override def projectSettings = Seq(
    scriptedBufferLog := true,
    scriptedPrescripted := { _ =>
      }
  )
}

trait ScriptedKeys {
  lazy val publishAll = TaskKey[Unit]("publish-all")
  lazy val publishLocalBinAll = taskKey[Unit]("")
  lazy val scripted = InputKey[Unit]("scripted")
  lazy val scriptedUnpublished = InputKey[Unit](
    "scripted-unpublished",
    "Execute scripted without publishing SBT first. Saves you some time when only your test has changed.")
  lazy val scriptedSource = SettingKey[File]("scripted-source")
  lazy val scriptedPrescripted = TaskKey[File => Unit]("scripted-prescripted")
  lazy val scriptedBufferLog = SettingKey[Boolean]("scripted-buffer-log")
  lazy val scriptedLaunchOpts = SettingKey[Seq[String]](
    "scripted-launch-opts",
    "options to pass to jvm launching scripted tasks")
}

object Scripted {
  lazy val MavenResolverPluginTest = config("mavenResolverPluginTest") extend Compile
  lazy val RepoOverrideTest = config("repoOverrideTest") extend Compile

  import sbt.complete._
  import DefaultParsers._
  // Paging, 1-index based.
  case class ScriptedTestPage(page: Int, total: Int)
  // FIXME: Duplicated with ScriptedPlugin.scriptedParser, this can be
  // avoided once we upgrade build.properties to 0.13.14
  def scriptedParser(scriptedBase: File): Parser[Seq[String]] = {
    val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
    val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map {
      (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
    }
    val pairMap = pairs.groupBy(_._1).mapValues(_.map(_._2).toSet);

    val id = charClass(c => !c.isWhitespace && c != '/').+.string
    val groupP = token(id.examples(pairMap.keySet.toSet)) <~ token('/')

    // A parser for page definitions
    val pageP: Parser[ScriptedTestPage] = ("*" ~ NatBasic ~ "of" ~ NatBasic) map {
      case _ ~ page ~ _ ~ total => ScriptedTestPage(page, total)
    }
    // Grabs the filenames from a given test group in the current page definition.
    def pagedFilenames(group: String, page: ScriptedTestPage): Seq[String] = {
      val files = pairMap(group).toSeq.sortBy(_.toLowerCase)
      val pageSize = files.size / page.total
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
        //if !files.isEmpty
      } yield files map (f => group + '/' + f)

    val testID = (for (group <- groupP; name <- nameP(group)) yield (group, name))
    val testIdAsGroup = matched(testID) map (test => Seq(test))
    //(token(Space) ~> matched(testID)).*
    (token(Space) ~> (PagedIds | testIdAsGroup)).* map (_.flatten)
  }

  // Interface to cross class loader
  type SbtScriptedRunner = {
    def runInParallel(resourceBaseDirectory: File,
                      bufferLog: Boolean,
                      tests: Array[String],
                      bootProperties: File,
                      launchOpts: Array[String],
                      prescripted: java.util.List[File]): Unit
  }

  def doScripted(launcher: File,
                 scriptedSbtClasspath: Seq[Attributed[File]],
                 scriptedSbtInstance: ScalaInstance,
                 sourcePath: File,
                 bufferLog: Boolean,
                 args: Seq[String],
                 prescripted: File => Unit,
                 launchOpts: Seq[String]): Unit = {
    System.err.println(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")
    val noJLine = new classpath.FilteredLoader(scriptedSbtInstance.loader, "jline." :: Nil)
    val loader = classpath.ClasspathUtilities.toLoader(scriptedSbtClasspath.files, noJLine)
    val bridgeClass = Class.forName("sbt.test.ScriptedRunner", true, loader)
    val bridge = bridgeClass.getDeclaredConstructor().newInstance().asInstanceOf[SbtScriptedRunner]
    try {
      // Using java.util.List to encode File => Unit.
      val callback = new java.util.AbstractList[File] {
        override def add(x: File): Boolean = {
          prescripted(x)
          false
        }
        def get(x: Int): sbt.File = ???
        def size(): Int = 0
      }
      bridge.runInParallel(sourcePath,
                           bufferLog,
                           args.toArray,
                           launcher,
                           launchOpts.toArray,
                           callback)
    } catch { case ite: java.lang.reflect.InvocationTargetException => throw ite.getCause }
  }
}
