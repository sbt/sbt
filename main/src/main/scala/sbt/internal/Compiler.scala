/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import java.io.{ File, PrintWriter }
import java.nio.file.{ Path, Paths }
import java.util.ArrayList
import sbt.BuildExtra.*
import sbt.Keys.Classpath
import sbt.internal.CommandStrings
import sbt.internal.inc.{ AnalyzingCompiler, ScalaInstance, ZincLmUtil }
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.worker.{ ClientJobParams, ScalaInstanceConfig }
import sbt.internal.worker1.{ ConsoleInfo, WorkerMain }
import sbt.internal.util.{ Attributed, RunHandler, Terminal as ITerminal }
import sbt.io.IO
import sbt.protocol.Serialization
import sbt.librarymanagement.{
  Artifact,
  Configuration,
  Configurations,
  ConfigurationReport,
  CrossVersion,
  DependencyResolution,
  ModuleID,
  ScalaArtifacts,
  SemanticSelector,
  VersionNumber
}
import sbt.util.Logger
import scala.jdk.CollectionConverters.*
import scala.util.Random
import xsbti.{ HashedVirtualFileRef, ScalaProvider }

object Compiler:
  private val r = Random()

  def scalaInstanceTask(
      configKey: TaskKey[ScalaInstanceConfig]
  ): Def.Initialize[Task[ScalaInstance]] =
    Def.task {
      val config = configKey.value
      makeScalaInstance(
        config.scalaVersion,
        config.libraryJars.map(File(_)).toArray,
        config.allCompilerJars.map(File(_)),
        config.extraToolJars.map(File(_)),
        Keys.state.value,
        Keys.scalaInstanceTopLoader.value,
      )
    }

  def scalaInstanceConfigTask(
      extraToolConf: Option[Configuration]
  ): Def.Initialize[Task[ScalaInstanceConfig]] =
    Def.taskDyn {
      val sh = Keys.scalaHome.value
      val app = Keys.appConfiguration.value
      val managed = Keys.managedScalaInstance.value
      sh match
        case Some(h) => scalaInstanceConfigFromHome(h)
        case _ =>
          val scalaProvider = app.provider.scalaProvider
          if !managed then emptyScalaInstanceConfig
          else scalaInstanceConfigFromUpdate(extraToolConf)
    }

  /**
   * A dummy ScalaInstance for Java-only projects.
   */
  def emptyScalaInstanceConfig: Def.Initialize[Task[ScalaInstanceConfig]] =
    Def.task {
      ScalaInstanceConfig(
        "0.0.0",
        Vector.empty,
        Vector.empty,
        Vector.empty,
      )
    }

  // Use the same class loader as the Scala classes used by sbt
  // This will fail for "doc" task https://github.com/sbt/sbt/issues/7725
  // because Scala 3 uses ScalaDocTool configuration to manage doc tools.
  def optimizedScalaInstance(
      sv: String,
      scalaProvider: ScalaProvider
  ): Def.Initialize[Task[ScalaInstance]] = Def.task {
    val allJars = scalaProvider.jars
    val libraryJars = allJars.filter { jar =>
      jar.getName == "scala-library.jar" || jar.getName.startsWith("scala3-library_3")
    }
    val compilerJar = allJars.filter { jar =>
      jar.getName == "scala-compiler.jar" || jar.getName.startsWith("scala3-compiler_3")
    }
    compilerJar match
      case Array(compilerJar) if libraryJars.nonEmpty =>
        makeScalaInstance(
          sv,
          libraryJars,
          allJars.toSeq,
          Seq.empty,
          Keys.state.value,
          Keys.scalaInstanceTopLoader.value,
        )
      case _ => ScalaInstance(sv, scalaProvider)
  }

  /**
   * Resolves extra REPL tool jars required for Scala 3.8+.
   *
   * In Scala 3.8+, `dotty.tools.repl.ReplDriver` was extracted from
   * `scala3-compiler_3` into a new artifact `scala3-repl_3`. The sbt launcher's
   * Scala provider only includes `scala3-compiler_3`, so we need to resolve
   * `scala3-repl_3` explicitly for `consoleProject` to work.
   *
   * Returns an empty sequence for Scala versions < 3.8.
   *
   * @see https://github.com/sbt/sbt/issues/7722
   * @see https://github.com/scala/scala3/pull/24243
   */
  private[sbt] def scala3ReplToolJars(
      scalaVersion: String,
      scalaOrg: String,
      dr: DependencyResolution,
      retrieveDir: File,
      log: Logger
  ): Seq[File] =
    if !ScalaArtifacts.isScala3_8Plus(scalaVersion) then Nil
    else
      // Scala 3 artifacts use the `_3` suffix, not `_3.8`. Bake the suffix into
      // the artifact name and disable cross-version resolution, because the
      // caller's scalaModuleInfo may be None or reflect a different project
      // Scala version than the one we actually need.
      val replModule = ModuleID(scalaOrg, s"${ScalaArtifacts.Scala3ReplID}_3", scalaVersion)
        .withCrossVersion(CrossVersion.disabled)
      dr.retrieve(replModule, scalaModuleInfo = None, retrieveDir, log) match
        case Right(resolved) => resolved.toSeq
        case Left(unresolved) =>
          log.warn(
            s"Could not resolve $replModule for consoleProject; REPL may fail to start: ${unresolved.resolveException.getMessage}"
          )
          Nil

  /**
   * Resolves the pre-built Scala 3 compiler bridge jar for `consoleProject`.
   *
   * The default `scalaCompilerBridgeBin` uses the project's Scala version, but
   * `consoleProject` compiles the build definition with sbt's own Scala
   * version. Resolve the pre-built bridge directly for that version to keep
   * the bridge consistent with `consoleProject / scalaInstance`.
   *
   * The bridge is a Java-compiled jar (cross-version disabled).
   *
   * @see https://github.com/sbt/sbt/issues/7722
   */
  private[sbt] def scala3ConsoleProjectBridgeJar(
      scalaVersion: String,
      scalaOrg: String,
      dr: DependencyResolution,
      retrieveDir: File,
      log: Logger
  ): Option[File] =
    if !ScalaArtifacts.isScala3(scalaVersion) then None
    else
      val bridgeModule = ModuleID(scalaOrg, "scala3-sbt-bridge", scalaVersion)
        .withCrossVersion(CrossVersion.disabled)
      dr.retrieve(bridgeModule, scalaModuleInfo = None, retrieveDir, log) match
        case Right(resolved) => resolved.find(_.getName.startsWith("scala3-sbt-bridge"))
        case Left(unresolved) =>
          log.warn(
            s"Could not resolve $bridgeModule for consoleProject: ${unresolved.resolveException.getMessage}"
          )
          None

  def scalaInstanceConfigFromHome(dir: File): Def.Initialize[Task[ScalaInstanceConfig]] =
    Def.task {
      val dummy = ScalaInstance(dir)(Keys.state.value.classLoaderCache.apply)
      Seq(dummy.loader, dummy.loaderLibraryOnly).foreach {
        case a: AutoCloseable => a.close()
        case _                =>
      }
      ScalaInstanceConfig(
        dummy.version,
        dummy.libraryJars.toVector.map(_.toPath().toUri()),
        dummy.compilerJars.toVector.map(_.toPath().toUri()),
        dummy.allJars.toVector.map(_.toPath().toUri()),
      )
    }

  def scalaInstanceFromUpdate(
      extraToolConf: Option[Configuration]
  ): Def.Initialize[Task[ScalaInstance]] =
    Def.task {
      val config = scalaInstanceConfigFromUpdate(extraToolConf).value
      makeScalaInstance(
        config.scalaVersion,
        config.libraryJars.map(File(_)).toArray,
        config.allCompilerJars.map(File(_)),
        config.extraToolJars.map(File(_)),
        Keys.state.value,
        Keys.scalaInstanceTopLoader.value,
      )
    }

  def compilerBridgeFromUpdate: Def.Initialize[Task[Seq[HashedVirtualFileRef]]] =
    Def.task {
      val fullReport = Keys.update.value
      val report = fullReport.configuration(Configurations.ZincTool)
      val allJars = report match
        case Some(r) => r.modules.flatMap(_.artifacts.map(_._2))
        case None    => Nil
      val conv = Keys.fileConverter.value
      allJars.map(x => (conv.toVirtualFile(x.toPath()): HashedVirtualFileRef))
    }

  def scalaInstanceConfigFromUpdate(
      extraToolConf: Option[Configuration]
  ): Def.Initialize[Task[ScalaInstanceConfig]] = Def.task {
    val sv = Keys.scalaVersion.value
    val fullReport = Keys.update.value
    val s = Keys.streams.value

    def reportScalaLibEviction(
        proj: String,
        sv: String,
        libVer: String,
        libName: String,
        err: Boolean,
        alignmentLine: String,
        evictedLine: String
    ): Unit =
      val fix =
        if err then
          """Upgrade the `scalaVersion` to fix the build. If upgrading the Scala compiler version is
            |not possible (for example due to a regression in the compiler or a missing dependency),
            |this error can be demoted by setting `allowUnsafeScalaLibUpgrade := true`.""".stripMargin
        else s"""Note that the dependency classpath and the runtime classpath of your project
             |contain the newer $libName $libVer, even if the scalaVersion is $sv.
             |Compilation (macro expansion) or using the Scala REPL in sbt may fail with a LinkageError.""".stripMargin
      val msg =
        s"""Expected `$proj scalaVersion` to be $libVer or later, but found $sv.
           |$alignmentLine
           |
           |$fix
           |
           |$evictedLine
           |""".stripMargin
      if err then sys.error(msg)
      else s.log.warn(msg)

    // For Scala 3, update scala-library.jar in `scala-tool` and `scala-doc-tool` in case a newer version
    // is present in the `compile` configuration. This is needed once forwards binary compatibility is dropped
    // to avoid NoSuchMethod exceptions when expanding macros.
    def updateLibraryToCompileConfiguration(report: ConfigurationReport) =
      if !ScalaArtifacts.isScala3(sv) then report
      else
        (for {
          compileConf <- fullReport.configuration(Configurations.Compile)
          compileLibMod <- compileConf.modules.find(_.module.name == ScalaArtifacts.LibraryID)
          reportLibMod <- report.modules.find(_.module.name == ScalaArtifacts.LibraryID)
          if VersionNumber(reportLibMod.module.revision)
            .matchesSemVer(SemanticSelector(s"<${compileLibMod.module.revision}"))
        } yield {
          val newMods = report.modules
            .filterNot(_.module.name == ScalaArtifacts.LibraryID) :+ compileLibMod
          report.withModules(newMods)
        }).getOrElse(report)

    val toolReport = updateLibraryToCompileConfiguration(
      fullReport
        .configuration(Configurations.ScalaTool)
        .getOrElse(sys.error(noToolConfiguration(Keys.managedScalaInstance.value)))
    )

    if Classpaths.isScala213(sv) then
      val scalaDeps = for
        compileReport <- fullReport.configuration(Configurations.Compile).iterator
        libName <- ScalaArtifacts.Artifacts.iterator
        lib <- compileReport.modules.find(_.module.name == libName)
      yield lib
      for lib <- scalaDeps.take(1) do
        val libVer = lib.module.revision
        val libName = lib.module.name
        val proj =
          Def.displayBuildRelative(Keys.thisProjectRef.value.build, Keys.thisProjectRef.value)
        if VersionNumber(sv).matchesSemVer(SemanticSelector(s"<$libVer")) then
          reportScalaLibEviction(
            proj,
            sv,
            libVer,
            libName,
            !Keys.allowUnsafeScalaLibUpgrade.value,
            s"""To support backwards-only binary compatibility (SIP-51), the Scala 2.13 compiler
              |should not be older than $libName on the dependency classpath.""".stripMargin,
            s"See `$proj evicted` to know why $libName $libVer is getting pulled in."
          )
        else ()
    else if ScalaArtifacts.isScala3(sv) then
      val scala3LibOpt = for
        compileReport <- fullReport.configuration(Configurations.Compile)
        lib <- compileReport.modules.find(
          _.module.name.startsWith(ScalaArtifacts.Scala3LibraryPrefix)
        )
      yield lib
      for lib <- scala3LibOpt do
        val libVer = lib.module.revision
        val libName = lib.module.name
        val proj =
          Def.displayBuildRelative(Keys.thisProjectRef.value.build, Keys.thisProjectRef.value)
        if VersionNumber(sv).matchesSemVer(SemanticSelector(s"<$libVer")) then
          reportScalaLibEviction(
            proj,
            sv,
            libVer,
            libName,
            !Keys.allowUnsafeScalaLibUpgrade.value,
            s"To maintain the compile-time alignment, the Scala compiler cannot be older than $libName on the dependency classpath.",
            s"See `$proj evicted` to know why $libName was upgraded from $sv to $libVer."
          )
        else ()
    else ()
    def file(id: String): Option[File] =
      for
        m <- toolReport.modules.find(_.module.name.startsWith(id))
        (_, file) <- m.artifacts.find(_._1.`type` == Artifact.DefaultType)
      yield file

    val allCompilerJars = toolReport.modules.flatMap(_.artifacts.map(_._2))
    val extraToolJars = extraToolConf match
      case Some(extra) =>
        fullReport
          .configuration(extra)
          .map(updateLibraryToCompileConfiguration)
          .toSeq
          .flatMap(_.modules)
          .flatMap(_.artifacts.map(_._2))
      case None => Nil
    val libraryJars = ScalaArtifacts.libraryIds(sv).flatMap(file)
    ScalaInstanceConfig(
      sv,
      libraryJars.toVector.map(_.toPath().toUri()),
      allCompilerJars.map(_.toPath().toUri()),
      extraToolJars.toVector.map(_.toPath().toUri())
    )
  }

  def makeScalaInstance(
      version: String,
      libraryJars: Array[File],
      allCompilerJars: Seq[File],
      extraToolJars: Seq[File],
      state: State,
      topLoader: ClassLoader,
  ): ScalaInstance =
    val classLoaderCache = State.StateOpsImpl(state).extendedClassLoaderCache
    val compilerJars = allCompilerJars.filterNot(libraryJars.contains).distinct.toArray
    val toolJars = extraToolJars
      .filterNot(jar => libraryJars.contains(jar) || compilerJars.contains(jar))
      .distinct
      .toArray
    val allJars = libraryJars ++ compilerJars ++ toolJars

    val libraryLoader = classLoaderCache(libraryJars.toList, topLoader)
    val compilerLoader = classLoaderCache(compilerJars.toList, libraryLoader)
    val fullLoader =
      if toolJars.isEmpty then compilerLoader
      else classLoaderCache(toolJars.distinct.toList, compilerLoader)
    new ScalaInstance(
      version = version,
      loader = fullLoader,
      loaderCompilerOnly = compilerLoader,
      loaderLibraryOnly = libraryLoader,
      libraryJars = libraryJars,
      compilerJars = compilerJars,
      allJars = allJars,
      explicitActual = Some(version)
    )

  private def noToolConfiguration(autoInstance: Boolean): String =
    val pre = "Missing Scala tool configuration from the 'update' report.  "
    val post =
      if autoInstance then
        "'scala-tool' is normally added automatically, so this may indicate a bug in sbt or you may be removing it from ivyConfigurations, for example."
      else
        "Explicitly define scalaInstance or scalaHome or include Scala dependencies in the 'scala-tool' configuration."
    pre + post

  def scalaCompilerBridgeJarsTask(
      sourceKey: Def.Initialize[ModuleID],
      log: Logger
  ): Def.Initialize[Task[Seq[HashedVirtualFileRef]]] =
    Def.task {
      val st = Keys.state.value
      val g = BuildPaths.getGlobalBase(st)
      val zincDir = BuildPaths.getZincDirectory(st, g)
      val app = Keys.appConfiguration.value
      val launcher = app.provider.scalaProvider.launcher
      val dr = Keys.scalaCompilerBridgeDependencyResolution.value
      val jars = ZincLmUtil.scalaCompilerBridgeJars(
        scalaInstance = Keys.scalaInstance.value,
        globalLock = launcher.globalLock,
        componentProvider = app.provider.components,
        secondaryCacheDir = Option(zincDir),
        dependencyResolution = dr,
        compilerBridgeSource = Keys.scalaCompilerBridgeSource.value,
        scalaJarsTarget = zincDir,
        log = log
      )
      val conv = Keys.fileConverter.value
      jars.map(jar => (conv.toVirtualFile(jar.toPath()): HashedVirtualFileRef))
    }

  def consoleTask: Def.Initialize[Task[Unit]] =
    consoleTask(Keys.console, Keys.exportedProductJars, Keys.fullClasspath)

  def consoleTask(
      task: TaskKey[?],
      products: Def.Initialize[Task[Classpath]],
      classpath: Def.Initialize[Task[Classpath]],
  ): Def.Initialize[Task[Unit]] =
    Def.taskIf {
      if (task / Keys.fork).value || (Keys.state.value.isNetworkCommand && (task / Keys.clientSide).value)
      then forkedConsoleTask(task, products, classpath).value
      else serverSideConsoleTask(task, products, classpath).value
    }

  private def serverSideConsoleTask(
      task: TaskKey[?],
      products: Def.Initialize[Task[Classpath]],
      classpath: Def.Initialize[Task[Classpath]],
  ): Def.Initialize[Task[Unit]] =
    Def.task {
      val si = (task / Keys.scalaInstance).value
      val s = Keys.streams.value
      val cp = Attributed.data(classpath.value)
      val converter = Keys.fileConverter.value
      val cpFiles = cp.map(converter.toPath).map(_.toFile())
      val fullcp = (cpFiles ++ si.allJars).distinct
      val tempDir = IO.createUniqueDirectory((task / Keys.taskTemporaryDirectory).value).toPath
      val loader = ClasspathUtil.makeLoader(fullcp.map(_.toPath), si, tempDir)
      val compiler =
        (task / Keys.compilers).value.scalac match
          case ac: AnalyzingCompiler => ac.onArgs(exported(s, "scala"))
      val sc = (task / Keys.scalacOptions).value
      val ic = (task / Keys.initialCommands).value
      val cc = (task / Keys.cleanupCommands).value
      (new Console(compiler))(cpFiles, sc, loader, ic, cc)()(using s.log).get
      println()
    }

  private def forkedConsoleTask(
      task: TaskKey[?],
      products: Def.Initialize[Task[Classpath]],
      classpath: Def.Initialize[Task[Classpath]],
  ): Def.Initialize[Task[Unit]] =
    Def.task {
      val s = Keys.streams.value
      val conv = Keys.fileConverter.value
      val cside = (task / Keys.clientSide).value
      val depsJars = (task / Keys.externalDependencyClasspath).value.toVector
        .map(_.data)
        .map(conv.toPath)
      val siConfig = (Keys.console / Keys.scalaInstanceConfig).value
      val bridgeJars = Keys.scalaCompilerBridgeJars.value
      val state = Keys.state.value
      val toolJars = siConfig.libraryJars ++ siConfig.allCompilerJars ++ siConfig.extraToolJars
      val toolJarsVf = toolJars.map(u => conv.toVirtualFile(Paths.get(u)): HashedVirtualFileRef)
      val fo = (task / Keys.forkOptions).value
      val service = Keys.bgJobService.value
      val workingDir = service.createWorkingDirectory
      val cp = service.copyClasspath(
        products.value,
        classpath.value,
        workingDir,
        conv,
      )
      val param = ConsoleInfo(
        ArrayList(toolJars.asJava),
        ArrayList(bridgeJars.toVector.map(vf => conv.toPath(vf).toUri()).asJava),
        ArrayList(),
        ArrayList(Attributed.data(cp).toVector.map(vf => conv.toPath(vf).toUri()).asJava),
        ArrayList((task / Keys.scalacOptions).value.asJava),
        (task / Keys.initialCommands).value,
        (task / Keys.cleanupCommands).value,
      )
      val randomId = r.nextLong()
      val workerMainClass = classOf[WorkerMain].getCanonicalName
      val workerCp0 = workerClasspath.map: p =>
        Attributed.blank(conv.toVirtualFile(p): HashedVirtualFileRef)
      val workerCp = workerCp0 ++ Attributed.blankSeq(bridgeJars ++ toolJarsVf).toVector
      val g = WorkerMain.mkGson()
      val paramJson = g.toJson(param, param.getClass)
      val json = jsonRpcRequest(randomId, "console", paramJson)
      val params = workingDir.toPath.resolve("console-params.json")
      IO.write(params.toFile, json)
      val info =
        RunUtil.mkRunInfo(Vector(s"@$params"), workerMainClass, workerCp, fo, conv, None)
      if cside && state.isNetworkCommand then
        val result = ClientJobParams(
          runInfo = info
        )
        import sbt.internal.worker.codec.JsonProtocol.given
        state.notifyEvent(Serialization.clientJob, result)
      else
        val terminal = ITerminal.console
        s.log.info("running console (fork)")
        try
          terminal.restore()
          RunHandler.jvmRun(info.jvmRunInfo.get, s.log).get
        finally terminal.restore()
        println()
    }

  private[sbt] def exported(w: PrintWriter, command: String): Seq[String] => Unit =
    args => w.println((command +: args).mkString(" "))

  private[sbt] def exported(s: Keys.TaskStreams, command: String): Seq[String] => Unit =
    val w = s.text(CommandStrings.ExportStream)
    try exported(w, command)
    finally w.close() // workaround for gh-937

  private def workerClasspath: Vector[Path] =
    Vector(
      IO.classLocationPath(classOf[WorkerMain]),
      IO.classLocationPath(classOf[org.scalasbt.shadedgson.com.google.gson.Gson]),
      IO.classLocationPath(classOf[xsbti.compile.ScalaInstance]),
      IO.classLocationPath(classOf[xsbti.Logger]),
      IO.classLocationPath(classOf[sbt.testing.Framework]),
    )

  private def jsonRpcRequest(id: Long, method: String, params: String): String =
    s"""{ "jsonrpc": "2.0", "method": "$method", "params": $params, "id": $id }"""

  def docTask(key: TaskKey[File]): Def.Initialize[Task[File]] =
    Def.taskIf {
      if (key / Keys.skip).value then
        val out = (key / Keys.target).value
        Keys.streams.value.log.debug(s"Skipping doc for ${Keys.thisProjectRef.value.project}")
        out
      else
        val s = Keys.streams.value
        val cs: xsbti.compile.Compilers = Keys.compilers.value
        val srcs = Keys.sources.value
        val out = (key / Keys.target).value
        val sOpts = Keys.scalacOptions.value
        val xapis = Keys.apiMappings.value
        val hasScala = srcs.exists(_.getName.endsWith(".scala"))
        val hasJava = srcs.exists(_.getName.endsWith(".java"))
        val cp = Attributed.data(Keys.dependencyClasspath.value).toList
        val reporter = (Keys.compile / Keys.bspReporter).value
        val converter = Keys.fileConverter.value
        val tFiles = Keys.tastyFiles.value
        val sv = Keys.scalaVersion.value
        (hasScala, hasJava) match {
          case (true, _) =>
            val xapisFiles = xapis.map { (k, v) =>
              converter.toPath(k).toFile() -> v
            }
            val externalApiOpts =
              if (ScalaArtifacts.isScala3(sv)) Opts.doc.externalAPIScala3(xapisFiles)
              else Opts.doc.externalAPI(xapisFiles)
            val options = sOpts ++ externalApiOpts
            def convertVfRef(value: String): String =
              if !value.contains("$") then value
              else converter.toPath(xsbti.VirtualFileRef.of(value)).toString
            val resolvedOptions = options.map { x =>
              if !x.contains("$") then x
              else x.split(":").map(_.split(",").map(convertVfRef).mkString(",")).mkString(":")
            }
            val scalac = cs.scalac match
              case ac: AnalyzingCompiler => ac.onArgs(exported(s, "scaladoc"))
            val docSrcFiles = if ScalaArtifacts.isScala3(sv) then tFiles else srcs
            // todo: cache this
            if docSrcFiles.nonEmpty then
              IO.delete(out)
              IO.createDirectory(out)
              // use PlainVirtualFile since Scaladoc currently doesn't handle actual VirtualFiles
              scalac.doc(
                docSrcFiles.map(_.toPath()).map(new sbt.internal.inc.PlainVirtualFile(_)),
                cp.map(converter.toPath).map(new sbt.internal.inc.PlainVirtualFile(_)),
                converter,
                out.toPath(),
                resolvedOptions,
                Keys.maxErrors.value,
                s.log,
              )
            else ()
          case (_, true) =>
            import sbt.internal.inc.javac.JavaCompilerArguments
            val javaSourcesOnly: xsbti.VirtualFile => Boolean = _.id.endsWith(".java")
            val classpath = cp.map(converter.toPath).map(converter.toVirtualFile)
            val options = Keys.javacOptions.value.toList
            cs.javaTools.javadoc.run(
              srcs.toArray
                .map { x =>
                  converter.toVirtualFile(x.toPath)
                }
                .filter(javaSourcesOnly),
              JavaCompilerArguments(Nil, classpath, options).toArray,
              sbt.internal.inc.CompileOutput(out.toPath),
              xsbti.compile.IncToolOptionsUtil.defaultIncToolOptions(),
              reporter,
              s.log,
            )
          case _ => () // do nothing
        }
        out
    }

  def consoleForkOptions: Def.Initialize[Task[ForkOptions]] = Def.task {
    // Build environment variables for proper terminal handling
    val termEnv = sys.env.get("TERM").getOrElse("xterm-256color")
    ForkOptions()
      .withConnectInput(true)
      .withRunJVMOptions(
        Vector(
          s"-Dorg.jline.terminal.type=$termEnv",
          "-Djline.terminal=auto",
        )
      )
      .withEnvVars(sys.env)
  }

  /**
   * Strips `-Ypickle-java` and `-Ypickle-write <path>` from scalac options so they don't
   * reach the REPL, where they cause file-lock errors on Windows and spurious
   * `InterruptedException`s on all platforms (see #8921).
   */
  private[sbt] def toConsoleScalacOptions(options: Seq[String]): Seq[String] =
    options match
      case "-Ypickle-write" +: (_ +: rest) => toConsoleScalacOptions(rest)
      case "-Ypickle-write" +: _           => Seq.empty
      case "-Ypickle-java" +: rest         => toConsoleScalacOptions(rest)
      case head +: rest                    => head +: toConsoleScalacOptions(rest)
      case _                               => Seq.empty

end Compiler
