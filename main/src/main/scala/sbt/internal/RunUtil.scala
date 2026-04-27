package sbt
package internal

import java.io.File
import sbt.BuildExtra.*
import sbt.Def.*
import sbt.Keys.*
import sbt.ScopeAxis.This
import sbt.SlashSyntax0.*
import sbt.internal.util.{ Terminal as ITerminal }
import sbt.internal.worker.{ ClientJobParams, FilePath, JvmRunInfo, RunInfo }
import sbt.io.IO
import sbt.protocol.Serialization
import sbt.util.CacheImplicits.given
import sbt.util.Logger
import xsbti.FileConverter

object RunUtil:
  /**
   * Split arguments at the first `--` delimiter.
   *  Tokens before `--` are JVM args; tokens after are app args.
   *  If no `--` is present, all tokens are app args (backward compatible).
   */
  private[sbt] def splitArgs(args: Seq[String]): (Seq[String], Seq[String]) =
    val idx = args.indexOf("--")
    if idx < 0 then (Nil, args)
    else (args.take(idx), args.drop(idx + 1))

  /**
   * Apply CLI JVM args to the ScalaRun and ForkOptions.
   *  For ForkRun: creates new ForkRun with augmented runJVMOptions.
   *  For Run (non-fork): warns and returns unchanged.
   */
  private[sbt] def applyJvmArgs(
      scalaRun: ScalaRun,
      jvmArgs: Seq[String],
      fo: ForkOptions,
      log: Logger,
  ): (ScalaRun, ForkOptions) =
    if jvmArgs.isEmpty then (scalaRun, fo)
    else
      scalaRun match
        case _: ForkRun =>
          val newFo = fo.withRunJVMOptions(fo.runJVMOptions ++ jvmArgs)
          (new ForkRun(newFo), newFo)
        case _ =>
          (scalaRun, fo)

  private def setWindowTitle(title: String): Unit =
    if System.console() != null && System.getenv("TERM") != null then
      scala.Console.print(s"\u001b]0;$title\u0007")
      scala.Console.flush()

  private[sbt] def mkWindowTitle(
      command: String,
      org: String,
      name: String,
      version: String
  ): String =
    s"sbt $command: $org % $name % $version"

  /**
   * Conventional server-side run implementation.
   */
  def serverSideRunTask(
      classpath: Initialize[Task[Classpath]],
      mainClassTask: Initialize[Task[Option[String]]],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[Unit]] =
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val (jvmArgs, appArgs) = splitArgs(parser.parsed)
      val mainClass = getMainClass(mainClassTask.value)
      val cp = classpath.value
      val fo = (run / forkOptions).value
      val log = streams.value.log
      given FileConverter = fileConverter.value
      val (modifiedRun, _) = applyJvmArgs(scalaRun.value, jvmArgs, fo, log)
      modifiedRun.run(mainClass, cp.files, appArgs, log).get
    }

  def configTasks(c: ScopeAxis[ConfigKey]): Seq[Setting[?]] = Seq(
    bgRunMain := bgRunMainTask(
      exportedProductJars,
      This / c / This / fullClasspathAsJars,
      bgRunMain / bgCopyClasspath,
      run / runner
    ).evaluated,
    // note that we use the same runner and mainClass as plain run
    bgRun := bgRunTask(
      exportedProductJars,
      This / c / This / fullClasspathAsJars,
      run / mainClass,
      bgRun / bgCopyClasspath,
      run / runner
    ).evaluated,
    runMain := defaultRunMainTask(
      exportedProductJars,
      This / c / This / fullClasspathAsJars,
      run / runner,
      runMain / clientSide
    ).evaluated,
    run := defaultRunTask(
      exportedProductJars,
      This / c / This / fullClasspathAsJars,
      run / mainClass,
      run / runner,
      run / clientSide
    ).evaluated,
    run / connectInput := true,
  )

  private def termWrapper(canonical: Boolean, echo: Boolean): (() => Unit) => (() => Unit) =
    (f: () => Unit) =>
      () => {
        val term = ITerminal.get
        if (!canonical) {
          term.enterRawMode()
          if (echo) term.setEchoEnabled(echo)
        } else if (!echo) term.setEchoEnabled(false)
        try f()
        finally {
          if (!canonical) term.exitRawMode()
          if (!echo) term.setEchoEnabled(true)
        }
      }

  private def getMainClass(value: Option[String]): String =
    value.getOrElse(sys.error("no main class detected"))

  private[sbt] def mkRunInfo(
      args: Vector[String],
      mainClass: String,
      cp: Classpath,
      fo: ForkOptions,
      conv: FileConverter,
      windowTitle: Option[String] = None
  ): RunInfo =
    val strategy = fo.outputStrategy.map(_.getClass().getSimpleName().filter(_ != '$'))
    val javaHome =
      fo.javaHome.map(IO.toURI).orElse(sys.props.get("java.home").map(x => IO.toURI(new File(x))))
    val jvmRunInfo = JvmRunInfo(
      args = args,
      classpath = cp.map(x => IO.toURI(conv.toPath(x.data).toFile)).map(FilePath(_, "")).toVector,
      mainClass = mainClass,
      connectInput = fo.connectInput,
      javaHome = javaHome,
      outputStrategy = strategy,
      workingDirectory = fo.workingDirectory.map(IO.toURI),
      jvmOptions = fo.runJVMOptions,
      environmentVariables = fo.envVars.toMap,
    )
    RunInfo(
      jvm = true,
      jvmRunInfo = Some(jvmRunInfo),
      nativeRunInfo = None,
      windowTitle = windowTitle,
    )

  def defaultRunMainTask(
      products: Initialize[Task[Classpath]],
      classpath: Initialize[Task[Classpath]],
      scalaRun: Initialize[Task[ScalaRun]],
      clientRun: Initialize[Boolean],
  ): Initialize[InputTask[Unit | ClientJobParams]] =
    val parser = Defaults.loadForParser(discoveredMainClasses)((s, names) =>
      Defaults.runMainParser(s, names getOrElse Nil)
    )
    Def.inputTask {
      val conv = fileConverter.value
      given FileConverter = conv
      val service = bgJobService.value
      val (mainClass, allArgs) = parser.parsed
      val (jvmArgs, appArgs) = splitArgs(allArgs)
      val hashClasspath = (bgRunMain / bgHashClasspath).value
      val fo = (run / forkOptions).value
      val log = streams.value.log
      val (modifiedRun, modifiedFo) = applyJvmArgs(scalaRun.value, jvmArgs, fo, log)
      val state = Keys.state.value
      val windowTitle = mkWindowTitle("runMain", organization.value, name.value, version.value)
      if clientRun.value && state.isNetworkCommand then
        val workingDir = service.createWorkingDirectory
        val cp = service.copyClasspath(
          products.value,
          classpath.value,
          workingDir,
          conv,
        )
        val info =
          mkRunInfo(appArgs.toVector, mainClass, cp, modifiedFo, conv, Some(windowTitle))
        val result = ClientJobParams(
          runInfo = info
        )
        import sbt.internal.worker.codec.JsonProtocol.given
        state.notifyEvent(Serialization.clientJob, result)
        result
      else
        setWindowTitle(windowTitle)
        val wrapper = termWrapper(canonicalInput.value, echoInput.value)
        val handle = service.runInBackgroundWithLoader(Keys.resolvedScoped.value, state):
          (logger, workingDir) =>
            val cp = service.copyClasspath(
              products.value,
              classpath.value,
              workingDir,
              hashClasspath,
              conv,
            )
            modifiedRun match
              case r: Run =>
                val loader = r.newLoader(cp.files)
                (
                  Some(loader),
                  wrapper(() => r.runWithLoader(loader, cp.files, mainClass, appArgs, logger).get)
                )
              case sr =>
                (None, wrapper(() => sr.run(mainClass, cp.files, appArgs, logger).get))
        service.waitForTry(handle).get
        ()
    }

  def defaultRunTask(
      products: Initialize[Task[Classpath]],
      classpath: Initialize[Task[Classpath]],
      mainClassTask: Initialize[Task[Option[String]]],
      scalaRun: Initialize[Task[ScalaRun]],
      clientRun: Initialize[Boolean]
  ): Initialize[InputTask[Unit | ClientJobParams]] =
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val conv = fileConverter.value
      given FileConverter = conv
      val (jvmArgs, appArgs) = splitArgs(parser.parsed)
      val service = bgJobService.value
      val mainClass = getMainClass(mainClassTask.value)
      val hashClasspath = (bgRun / bgHashClasspath).value
      val fo = (run / forkOptions).value
      val log = streams.value.log
      val (modifiedRun, modifiedFo) = applyJvmArgs(scalaRun.value, jvmArgs, fo, log)
      val state = Keys.state.value
      val windowTitle = mkWindowTitle("run", organization.value, name.value, version.value)
      if clientRun.value && state.isNetworkCommand then
        val workingDir = service.createWorkingDirectory
        val cp = service.copyClasspath(
          products.value,
          classpath.value,
          workingDir,
          conv,
        )
        val info = mkRunInfo(appArgs.toVector, mainClass, cp, modifiedFo, conv, Some(windowTitle))
        val result = ClientJobParams(
          runInfo = info
        )
        import sbt.internal.worker.codec.JsonProtocol.given
        state.notifyEvent(Serialization.clientJob, result)
        result
      else
        setWindowTitle(windowTitle)
        val wrapper = termWrapper(canonicalInput.value, echoInput.value)
        val handle = service.runInBackgroundWithLoader(Keys.resolvedScoped.value, state):
          (logger, workingDir) =>
            val cp = service.copyClasspath(
              products.value,
              classpath.value,
              workingDir,
              hashClasspath,
              conv
            )
            modifiedRun match
              case r: Run =>
                val loader = r.newLoader(cp.files)
                (
                  Some(loader),
                  wrapper(() => r.runWithLoader(loader, cp.files, mainClass, appArgs, logger).get)
                )
              case sr =>
                (None, wrapper(() => sr.run(mainClass, cp.files, appArgs, logger).get))
        service.waitForTry(handle).get
        ()
    }

  def bgRunMainTask(
      products: Initialize[Task[Classpath]],
      classpath: Initialize[Task[Classpath]],
      copyClasspath: Initialize[Boolean],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[JobHandle]] =
    val parser = Defaults.loadForParser(discoveredMainClasses)((s, names) =>
      Defaults.runMainParser(s, names getOrElse Nil)
    )
    Def.inputTask {
      val service = bgJobService.value
      val (mainClass, allArgs) = parser.parsed
      val (jvmArgs, appArgs) = splitArgs(allArgs)
      val hashClasspath = (bgRunMain / bgHashClasspath).value
      val fo = (run / forkOptions).value
      val log = streams.value.log
      val (modifiedRun, _) = applyJvmArgs(scalaRun.value, jvmArgs, fo, log)
      val wrapper = termWrapper(canonicalInput.value, echoInput.value)
      val converter = fileConverter.value
      setWindowTitle(mkWindowTitle("bgRunMain", organization.value, name.value, version.value))
      service.runInBackgroundWithLoader(Keys.resolvedScoped.value, state.value) {
        (logger, workingDir) =>
          val cp =
            if copyClasspath.value then
              service.copyClasspath(
                products.value,
                classpath.value,
                workingDir,
                hashClasspath,
                converter,
              )
            else classpath.value
          given FileConverter = fileConverter.value
          modifiedRun match
            case r: Run =>
              val loader = r.newLoader(cp.files)
              (
                Some(loader),
                wrapper(() => r.runWithLoader(loader, cp.files, mainClass, appArgs, logger).get)
              )
            case sr =>
              (None, wrapper(() => sr.run(mainClass, cp.files, appArgs, logger).get))
      }
    }

  def bgRunTask(
      products: Initialize[Task[Classpath]],
      classpath: Initialize[Task[Classpath]],
      mainClassTask: Initialize[Task[Option[String]]],
      copyClasspath: Initialize[Boolean],
      scalaRun: Initialize[Task[ScalaRun]]
  ): Initialize[InputTask[JobHandle]] =
    val parser = Def.spaceDelimited()
    Def.inputTask {
      val (jvmArgs, appArgs) = splitArgs(parser.parsed)
      val service = bgJobService.value
      val mainClass = getMainClass(mainClassTask.value)
      val hashClasspath = (bgRun / bgHashClasspath).value
      val fo = (run / forkOptions).value
      val log = streams.value.log
      val (modifiedRun, _) = applyJvmArgs(scalaRun.value, jvmArgs, fo, log)
      val wrapper = termWrapper(canonicalInput.value, echoInput.value)
      val converter = fileConverter.value
      setWindowTitle(mkWindowTitle("bgRun", organization.value, name.value, version.value))
      service.runInBackgroundWithLoader(Keys.resolvedScoped.value, state.value) {
        (logger, workingDir) =>
          val cp =
            if copyClasspath.value then
              service.copyClasspath(
                products.value,
                classpath.value,
                workingDir,
                hashClasspath,
                converter
              )
            else classpath.value
          given FileConverter = converter
          modifiedRun match
            case r: Run =>
              val loader = r.newLoader(cp.files)
              (
                Some(loader),
                wrapper(() => r.runWithLoader(loader, cp.files, mainClass, appArgs, logger).get)
              )
            case sr =>
              (None, wrapper(() => sr.run(mainClass, cp.files, appArgs, logger).get))
      }
    }
end RunUtil
