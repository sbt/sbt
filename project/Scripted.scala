import sbt._
import Keys._
import Def.Initialize

import scala.language.reflectiveCalls

object Scripted {
  def scriptedPath = file("scripted")
  lazy val scripted = InputKey[Unit]("scripted")
  lazy val scriptedUnpublished = InputKey[Unit]("scripted-unpublished", "Execute scripted without publishing SBT first. Saves you some time when only your test has changed.")
  lazy val scriptedSource = SettingKey[File]("scripted-source")
  lazy val scriptedPrescripted = TaskKey[File => Unit]("scripted-prescripted")

  lazy val MavenResolverPluginTest = config("mavenResolverPluginTest") extend Compile

  import sbt.complete._
  import DefaultParsers._
  // Paging, 1-index based.
  case class ScriptedTestPage(page: Int, total: Int)
  def scriptedParser(scriptedBase: File): Parser[Seq[String]] =
    {
      val pairs = (scriptedBase * AllPassFilter * AllPassFilter * "test").get map { (f: File) =>
        val p = f.getParentFile
        (p.getParentFile.getName, p.getName)
      };
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
        token("*".id | id.examples(pairMap(group)))
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
    def run(resourceBaseDirectory: File, bufferLog: Boolean, tests: Array[String], bootProperties: File,
      launchOpts: Array[String], prescripted: java.util.List[File]): Unit
  }

  def doScripted(launcher: File, scriptedSbtClasspath: Seq[Attributed[File]], scriptedSbtInstance: ScalaInstance, sourcePath: File, args: Seq[String], prescripted: File => Unit) {
    System.err.println(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")
    val noJLine = new classpath.FilteredLoader(scriptedSbtInstance.loader, "jline." :: Nil)
    val loader = classpath.ClasspathUtilities.toLoader(scriptedSbtClasspath.files, noJLine)
    val bridgeClass = Class.forName("sbt.test.ScriptedRunner", true, loader)
    val bridge = bridgeClass.newInstance.asInstanceOf[SbtScriptedRunner]
    val launcherVmOptions = Array("-XX:MaxPermSize=256M") // increased after a failure in scripted source-dependencies/macro
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
      bridge.run(sourcePath, true, args.toArray, launcher, launcherVmOptions, callback)
    } catch { case ite: java.lang.reflect.InvocationTargetException => throw ite.getCause }
  }
}
