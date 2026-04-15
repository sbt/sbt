/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import sbt.ProjectExtra.extract
import sbt.internal.classpath.AlternativeZincUtil
import sbt.internal.inc.{ ScalaInstance, ZincLmUtil }
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.util.Terminal
import sbt.io.IO
import sbt.librarymanagement.DependencyResolution
import sbt.util.Logger
import xsbti.HashedVirtualFileRef
import xsbti.compile.ClasspathOptionsUtil

object ConsoleProject:
  def consoleProjectTask =
    Def.task {
      val st = Keys.state.value
      val si = (Keys.consoleProject / Keys.scalaInstance).value
      val dr = (LocalRootProject / Keys.dependencyResolution).value
      val compilerBridgeBinaryBin =
        (LocalRootProject / Keys.consoleProject / Keys.scalaCompilerBridgeBin).value
      ConsoleProject(
        st,
        si,
        dr,
        compilerBridgeBinaryBin,
        (Keys.consoleProject / Keys.initialCommands).value
      )(using
        Keys.streams.value.log
      )
      println()
    }

  def apply(
      state: State,
      si: ScalaInstance,
      dr: DependencyResolution,
      compilerBridgeBinaryBin: Seq[HashedVirtualFileRef],
      extra: String,
      cleanupCommands: String = "",
      options: Seq[String] = Nil
  )(using
      log: Logger
  ): Unit = {
    val extracted = Project.extract(state)
    val cpImports = new Imports(extracted, state)
    // Bindings are ignored by Scala 3 bridge: https://github.com/scala/scala3/issues/5069
    // Workaround: vals are injected via initialCommands from ConsoleProjectBindings holder.
    // bindings are still passed to Console for Scala 2 backward compatibility.
    val bindings =
      ("currentState" -> state) :: ("extracted" -> extracted) :: ("cpHelpers" -> cpImports) :: Nil
    val unit = extracted.currentUnit
    val tempDir0 = extracted.get(Keys.consoleProject / Keys.taskTemporaryDirectory)
    val tempDir = IO.createUniqueDirectory(tempDir0).toPath()
    val conv = extracted.get(Keys.fileConverter)
    val g = BuildPaths.getGlobalBase(state)
    val zincDir = BuildPaths.getZincDirectory(state, g)
    val app = state.configuration
    val launcher = app.provider.scalaProvider.launcher
    val compiler = compilerBridgeBinaryBin.toList match
      case jar :: xs =>
        AlternativeZincUtil.scalaCompiler(
          scalaInstance = si,
          classpathOptions = ClasspathOptionsUtil.repl,
          compilerBridgeJar = conv.toPath(jar).toFile(),
          classLoaderCache = state.get(BasicKeys.classLoaderCache)
        )
      case Nil =>
        ZincLmUtil.scalaCompiler(
          scalaInstance = si,
          classpathOptions = ClasspathOptionsUtil.repl,
          globalLock = launcher.globalLock,
          componentProvider = app.provider.components,
          secondaryCacheDir = Option(zincDir),
          dependencyResolution = dr,
          compilerBridgeSource =
            extracted.get(Keys.consoleProject / Keys.scalaCompilerBridgeSource),
          scalaJarsTarget = zincDir,
          classLoaderCache = state.get(BasicKeys.classLoaderCache),
          log = log
        )
    ConsoleProjectBindings.set(state, extracted, cpImports)
    val baseImports = BuildUtil.getImports(unit.unit)
    val bindingDefs = Seq(
      "val currentState = _root_.sbt.internal.ConsoleProjectBindings.state",
      "val extracted = _root_.sbt.internal.ConsoleProjectBindings.extracted",
      "val cpHelpers = _root_.sbt.internal.ConsoleProjectBindings.cpHelpers",
    )
    val bindingImports = BuildUtil.importAll(bindings.map(_._1))
    val allLines = baseImports ++ bindingDefs ++ bindingImports
    val initCommands = allLines.mkString("", ";\n", ";\n\n") + extra
    // Two things are required so the REPL resolves `sbt.*` (e.g.
    // `sbt.TaskKey`, `sbt.Keys`, `sbt.State`, `sbt.internal.ConsoleProjectBindings`)
    // and `scala.*` (e.g. `scala.Function2`) via the *same* class objects
    // that sbt itself uses. See sbt/sbt#7722.
    //
    //   1. Remove sbt's own module jars from the runtime URL classloader,
    //      so that `sbt.*` references resolve via parent delegation back
    //      to sbt's `MetaBuildLoader` rather than being defined a second
    //      time by the REPL's URL classloader (which would break the
    //      `ConsoleProjectBindings` singleton's static state and
    //      trigger `LinkageError: loader constraint violation` when REPL
    //      code touches a method whose signature mentions a duplicated
    //      type — e.g. `sbt.TaskKey.zipWith(_, scala.Function2)`).
    //
    //   2. On Scala 3.8+ switch the REPL's bytecode interrupt
    //      instrumentation to `local` mode. The default (`true`) for
    //      `dotty.tools.repl.AbstractFileClassLoader` (added in 3.8)
    //      reads every class's bytes from the parent loader via
    //      `getResourceAsStream` and `defineClass`-es them a *second*
    //      time inside the REPL loader, producing duplicate `Class`
    //      objects for every `sbt.*` and `scala.*` class. `local` skips
    //      that re-definition and falls through to standard parent-first
    //      delegation (so the REPL sees the same singleton classes as
    //      the surrounding sbt process) while still keeping interrupt
    //      support for REPL-defined code — preserving Ctrl+C for long-
    //      running expressions like `(Compile / compile).eval`. The flag
    //      does not exist on Scala 3.7 and earlier (which use the older
    //      AFClassLoader without instrumentation), so we only pass it
    //      when the consoleProject scala instance is 3.8+ to avoid a
    //      "bad option" warning.
    //
    // The full classpath is still passed to `Console` below so the REPL's
    // compile-time classpath is unchanged.
    val runtimeClasspath = unit.classpath.filterNot(isSbtModuleJar)
    val loader = ClasspathUtil.makeLoader(runtimeClasspath, si, tempDir)
    val replOptions =
      if needsInterruptInstrumentationOptOut(si.version) then
        "-Xrepl-interrupt-instrumentation:local" +: options
      else options
    val terminal = Terminal.get
    // TODO - Hook up dsl classpath correctly...
    try
      (new Console(compiler))(
        unit.classpath.map(_.toFile),
        replOptions,
        initCommands,
        cleanupCommands,
        terminal
      )(Some(loader), bindings).get
      ()
    finally ConsoleProjectBindings.clear()
  }

  /**
   * `dotty.tools.repl.AbstractFileClassLoader`'s bytecode interrupt
   * instrumentation was added in Scala 3.8 and is enabled by default —
   * see Scala 3 PR scala/scala3#22720. The setting that disables it is
   * also a 3.8+ addition (see `Xrepl-interrupt-instrumentation` in
   * `dotty.tools.dotc.config.ScalaSettings`). For 3.7 and earlier the
   * REPL classloader doesn't re-define classes locally, so the flag is
   * unnecessary and would only produce a "bad option" warning.
   */
  private def needsInterruptInstrumentationOptOut(scalaVersion: String): Boolean =
    scalaVersion match
      case s"3.$rest" =>
        rest.takeWhile(_.isDigit).toIntOption.exists(_ >= 8)
      case _ => false

  /**
   * Returns true when a `Path` refers to a jar published by
   * `org.scala-sbt`. These jars ship sbt's own classes (e.g. `sbt.State`,
   * `sbt.TaskKey`, `sbt.Keys`) that are already reachable via the parent
   * class loader used by `consoleProject`. They must be excluded from the
   * REPL's runtime classloader so that `sbt.*` references resolve via
   * parent delegation and reach sbt's singleton copies — rather than
   * being defined fresh by the URL classloader from `unit.classpath`,
   * which would trigger a `LinkageError: loader constraint violation`
   * whenever those classes are used from the REPL. See sbt/sbt#7722.
   *
   * Detection is done via `META-INF/MANIFEST.MF`'s `Implementation-Vendor-Id`
   * attribute, which all sbt module jars set to `org.scala-sbt`. This is
   * more robust than checking for specific class entries, because it
   * uniformly catches every sbt module (main, main-settings, command, io,
   * util-*, etc.) without enumerating them.
   */
  private def isSbtModuleJar(p: java.nio.file.Path): Boolean =
    val name = p.getFileName.toString
    if !name.endsWith(".jar") || !java.nio.file.Files.isRegularFile(p) then false
    else
      try
        val zf = new java.util.zip.ZipFile(p.toFile)
        try
          val entry = zf.getEntry("META-INF/MANIFEST.MF")
          if entry eq null then false
          else
            val is = zf.getInputStream(entry)
            try
              val manifest = new java.util.jar.Manifest(is)
              val attrs = manifest.getMainAttributes
              attrs != null && "org.scala-sbt" == attrs.getValue("Implementation-Vendor-Id")
            finally is.close()
        finally zf.close()
      catch case _: java.io.IOException => false

  /** Conveniences for consoleProject that shouldn't normally be used for builds. */
  final class Imports private[sbt] (extracted: Extracted, state: State) {
    import extracted.*
    implicit def taskKeyEvaluate[T](t: TaskKey[T]): Evaluate[T] =
      new Evaluate(runTask(t, state)._2)
    implicit def settingKeyEvaluate[T](s: SettingKey[T]): Evaluate[T] = new Evaluate(get(s))
  }
  final class Evaluate[T] private[sbt] (val eval: T)
end ConsoleProject
