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
  lazy val scriptedAltLocalResolverRoot = settingKey[File]("Root of the alternative local repository")
  lazy val scriptedAltLocalResolver = settingKey[Resolver]("Alternative local resolver for scripted tests that override the local resolver.")
  lazy val scriptedAltPublishLocal = taskKey[Unit]("Publishes to the alternative local resolver.")
  lazy val scriptedPublishAllLocal = taskKey[Unit]("Publish all projects to scripted-only repository")
  lazy val scriptedPublishLocal = taskKey[Unit]("Published to the scripted-only local resolver.")
  lazy val scriptedAddAltLocalResolver = taskKey[File => Unit]("Adds the alternative local repository to a scripted test.")
  lazy val scriptedIvy = taskKey[IvySbt]("Ivy instance to use for scripted tests")
  lazy val scriptedIvyHome = settingKey[File]("Ivy home to use for scripted tests")
  lazy val scriptedSettings: Seq[Setting[_]] = inThisBuild(Seq(
    scriptedIvyHome := baseDirectory.value / "target" / "scripted-ivy",
    scriptedAltLocalResolverRoot := scriptedIvyHome.value / "sbt-alternative",
    scriptedAltLocalResolver := Resolver.file("alternative-local", scriptedAltLocalResolverRoot.value)(Resolver.ivyStylePatterns))) ++ Seq(
    scriptedIvy := {
      val ivyConf = ivyConfiguration.value match {
        case inline: InlineIvyConfiguration =>
          val newPaths = new IvyPaths(inline.paths.baseDirectory, Some(scriptedIvyHome.value))
          new InlineIvyConfiguration(newPaths, inline.resolvers, inline.otherResolvers, inline.moduleConfigurations, inline.localOnly, inline.lock,
            inline.checksums, inline.resolutionCacheDir, inline.updateOptions, inline.log)
        case external: ExternalIvyConfiguration =>
          error("Expected `InlineIvyConfiguration`.")
      }
      new IvySbt(ivyConf)
    },
    scriptedPublishLocal := {
      val ivy = scriptedIvy.value
      val module = new ivy.Module(moduleSettings.value)
      IvyActions.publish(module, publishLocalConfiguration.value, streams.value.log)
    },
    scriptedPublishAllLocal := {
      val _ = (scriptedPublishLocal).all(ScopeFilter(inAnyProject)).value
    },
    resolvers += scriptedAltLocalResolver.value,
    scriptedAltPublishLocal := {
      val config = publishLocalConfiguration.value
      val ivy = scriptedIvy.value
      val module = new ivy.Module(moduleSettings.value)
      val newConfig =
        new PublishConfiguration(
          config.ivyFile,
          scriptedAltLocalResolver.value.name,
          config.artifacts,
          config.checksums,
          config.logging)
      streams.value.log.info("Publishing " + name.value + " to local resolver: " + scriptedAltLocalResolver.value.name)

      IvyActions.publish(module, newConfig, streams.value.log)
    },
    scriptedAddAltLocalResolver := { (root: File) =>
      if (!root.exists) error(s"Couldn't add alternative local resolver: $root doesn't exist.")
      val plugin = root / "project" / "AddResolverPlugin.scala"
      if (!plugin.exists) {
        IO.write(plugin, s"""import sbt._
                            |import Keys._
                            |
                            |object AddResolverPlugin extends AutoPlugin {
                            |  override def requires = sbt.plugins.JvmPlugin
                            |  override def trigger = allRequirements
                            |
                            |  override lazy val projectSettings = Seq(resolvers += alternativeLocalResolver)
                            |  lazy val alternativeLocalResolver = Resolver.file("${scriptedAltLocalResolver.value.name}", file("${scriptedAltLocalResolverRoot.value.getAbsolutePath}"))(Resolver.ivyStylePatterns)
                            |}
                            |""".stripMargin)
      }
    })

  lazy val MavenResolverPluginTest = config("mavenResolverPluginTest") extend Compile

  import sbt.complete._
  import DefaultParsers._
  // Paging, 1-index based.
  case class ScriptedTestPage(page: Int, total: Int)
  def scriptedParser(scriptedBase: File): Parser[Seq[String]] =
    {
      val scriptedFiles: NameFilter = ("test": NameFilter) | "pending"
      val pairs = (scriptedBase * AllPassFilter * AllPassFilter * scriptedFiles).get map { (f: File) =>
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

  def doScripted(launcher: File, scriptedSbtClasspath: Seq[Attributed[File]], scriptedSbtInstance: ScalaInstance, sourcePath: File, args: Seq[String], prescripted: File => Unit, ivyHome: File): Unit = {
    System.err.println(s"About to run tests: ${args.mkString("\n * ", "\n * ", "\n")}")
    val noJLine = new classpath.FilteredLoader(scriptedSbtInstance.loader, "jline." :: Nil)
    val loader = classpath.ClasspathUtilities.toLoader(scriptedSbtClasspath.files, noJLine)
    val bridgeClass = Class.forName("sbt.test.ScriptedRunner", true, loader)
    val bridge = bridgeClass.newInstance.asInstanceOf[SbtScriptedRunner]
    val launcherVmOptions = Array(
      "-XX:MaxPermSize=256M", "-Xmx1G", // increased after a failure in scripted source-dependencies/macro
      s"-Dsbt.ivy.home=$ivyHome" // don't use the default ivy home, so that we don't depend on the machine's ivy cache
    )
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
