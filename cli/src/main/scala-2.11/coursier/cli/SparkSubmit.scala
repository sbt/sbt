package coursier.cli

import java.io.{PrintStream, BufferedReader, File, PipedInputStream, PipedOutputStream, InputStream, InputStreamReader}
import java.net.URLClassLoader
import java.nio.file.Files

import caseapp._

import coursier.{ Attributes, Dependency }
import coursier.cli.spark.{ Assembly, Submit }
import coursier.util.Parse

import scala.util.control.NonFatal

object SparkSubmit {

  def scalaSparkVersions(dependencies: Iterable[Dependency]): Either[String, (String, String)] = {

    val sparkCoreMods = dependencies.collect {
      case dep if dep.module.organization == "org.apache.spark" &&
        (dep.module.name == "spark-core_2.10" || dep.module.name == "spark-core_2.11") =>
        (dep.module, dep.version)
    }

    if (sparkCoreMods.isEmpty)
      Left("Cannot find spark among dependencies")
    else if (sparkCoreMods.size == 1) {
      val scalaVersion = sparkCoreMods.head._1.name match {
        case "spark-core_2.10" => "2.10"
        case "spark-core_2.11" => "2.11"
        case _ => throw new Exception("Cannot happen")
      }

      val sparkVersion = sparkCoreMods.head._2

      Right((scalaVersion, sparkVersion))
    } else
      Left(s"Found several spark code modules among dependencies (${sparkCoreMods.mkString(", ")})")

  }

}

/**
  * Submits spark applications.
  *
  * Can be run with no spark distributions around.
  *
  * @author Alexandre Archambault
  * @author Han Ju
  */
@CommandName("spark-submit")
case class SparkSubmit(
  @Recurse
    options: SparkSubmitOptions
) extends App with ExtraArgsApp {

  val helper: Helper = new Helper(
    options.common,
    remainingArgs,
    extraJars = options.extraJars.map(new File(_))
  )
  val jars =
    helper.fetch(sources = false, javadoc = false) ++
      options.extraJars.map(new File(_))

  val (scalaVersion, sparkVersion) =
    if (options.sparkVersion.isEmpty)
      SparkSubmit.scalaSparkVersions(helper.res.dependencies) match {
        case Left(err) =>
          Console.err.println(
            s"Cannot get spark / scala versions from dependencies: $err\n" +
              "Set them via --scala-version or --spark-version"
          )
          sys.exit(1)
        case Right(versions) => versions
      }
    else
      (options.common.scalaVersion, options.sparkVersion)

  val assemblyOrError =
    if (options.sparkAssembly.isEmpty) {

      // FIXME Also vaguely done in Helper and below

      val (errors, modVers) = Parse.moduleVersionConfigs(
        options.assemblyDependencies,
        options.common.scalaVersion
      )

      val deps = modVers.map {
        case (module, version, configOpt) =>
          Dependency(
            module,
            version,
            attributes = Attributes(options.common.defaultArtifactType, ""),
            configuration = configOpt.getOrElse(options.common.defaultConfiguration),
            exclusions = helper.excludes
          )
      }

      if (errors.isEmpty)
        Assembly.spark(
          scalaVersion,
          sparkVersion,
          options.noDefaultAssemblyDependencies,
          options.assemblyDependencies,
          options.common
        )
      else
        Left(s"Cannot parse assembly dependencies:\n${errors.map("  " + _).mkString("\n")}")
    } else {
      val f = new File(options.sparkAssembly)
      if (f.isFile)
        Right((f, Nil))
      else if (f.exists())
        Left(s"${options.sparkAssembly} is not a file")
      else
        Left(s"${options.sparkAssembly} not found")
    }

  val (assembly, assemblyJars) = assemblyOrError match {
    case Left(err) =>
      Console.err.println(s"Cannot get spark assembly: $err")
      sys.exit(1)
    case Right(res) => res
  }


  val idx = {
    val idx0 = extraArgs.indexOf("--")
    if (idx0 < 0)
      extraArgs.length
    else
      idx0
  }

  assert(idx >= 0)

  val sparkOpts = extraArgs.take(idx)
  val jobArgs = extraArgs.drop(idx + 1)

  val mainClass =
    if (options.mainClass.isEmpty)
      helper.retainedMainClass
    else
      options.mainClass

  val mainJar = helper
    .loader
    .loadClass(mainClass) // FIXME Check for errors, provide a nicer error message in that case
    .getProtectionDomain
    .getCodeSource
    .getLocation
    .getPath              // TODO Safety check: protocol must be file

  val (check, extraJars0) = jars.partition(_.getAbsolutePath == mainJar)

  val extraJars = extraJars0.filterNot(assemblyJars.toSet)

  if (check.isEmpty)
    Console.err.println(
      s"Warning: cannot find back $mainJar among the dependencies JARs (likely a coursier bug)"
    )

  val extraSparkOpts = Seq(
    "--conf", "spark.yarn.jar=" + assembly.getAbsolutePath
  )

  val extraJarsOptions =
    if (extraJars.isEmpty)
      Nil
    else
      Seq("--jars", extraJars.mkString(","))

  val mainClassOptions = Seq("--class", mainClass)

  val sparkSubmitOptions = sparkOpts ++ extraSparkOpts ++ extraJarsOptions ++ mainClassOptions ++
    Seq(mainJar) ++ jobArgs

  val submitCp = Submit.cp(
    scalaVersion,
    sparkVersion,
    options.noDefaultSubmitDependencies,
    options.submitDependencies,
    options.common
  )

  val submitLoader = new URLClassLoader(
    submitCp.map(_.toURI.toURL).toArray,
    helper.baseLoader
  )

  Launch.run(
    submitLoader,
    Submit.mainClassName,
    sparkSubmitOptions,
    options.common.verbosityLevel,
    {
      if (options.common.verbosityLevel >= 1)
        Console.err.println(
          s"Launching spark-submit with arguments:\n" +
            sparkSubmitOptions.map("  " + _).mkString("\n")
        )

      OutputHelper.handleOutput(
        Some(options.yarnIdFile).filter(_.nonEmpty).map(new File(_)),
        Some(options.maxIdleTime).filter(_ > 0)
      )
    }
  )
}


object OutputHelper {

  def outputInspectThread(
    name: String,
    from: InputStream,
    to: PrintStream,
    handlers: Seq[String => Unit]
  ) = {

    val t = new Thread {
      override def run() = {
        val in = new BufferedReader(new InputStreamReader(from))
        var line: String = null
        while ({
          line = in.readLine()
          line != null
        }) {
          to.println(line)
          handlers.foreach(_(line))
        }
      }
    }

    t.setName(name)
    t.setDaemon(true)

    t
  }


  def handleOutput(yarnAppFileOpt: Option[File], maxIdleTimeOpt: Option[Int]): Unit = {

    var handlers = Seq.empty[String => Unit]
    var threads = Seq.empty[Thread]

    for (yarnAppFile <- yarnAppFileOpt) {

      val Pattern = ".*Application report for ([^ ]+) .*".r

      @volatile var written = false
      val lock = new AnyRef
      def handleMessage(s: String): Unit =
        if (!written)
          s match {
            case Pattern(id) =>
              lock.synchronized {
                if (!written) {
                  println(s"Detected YARN app ID $id")
                  val path = yarnAppFile.toPath
                  Option(path.getParent).foreach(_.toFile.mkdirs())
                  Files.write(path, id.getBytes("UTF-8"))
                  written = true
                }
              }
            case _ =>
          }

      val f = { line: String =>
        try handleMessage(line)
        catch {
          case NonFatal(_) =>
        }
      }

      handlers = handlers :+ f
    }

    for (maxIdleTime <- maxIdleTimeOpt if maxIdleTime > 0) {

      @volatile var lastMessageTs = -1L

      def updateLastMessageTs() = {
        lastMessageTs = System.currentTimeMillis()
      }

      val checkThread = new Thread {
        override def run() =
          try {
            while (true) {
              lastMessageTs = -1L
              Thread.sleep(maxIdleTime * 1000L)
              if (lastMessageTs < 0) {
                Console.err.println(s"No output from spark-submit for more than $maxIdleTime s, exiting")
                sys.exit(1)
              }
            }
          } catch {
            case t: Throwable =>
              Console.err.println(s"Caught $t in check spark-submit output thread!")
              throw t
          }
      }

      checkThread.setName("check-spark-submit-output")
      checkThread.setDaemon(true)

      threads = threads :+ checkThread

      val f = { line: String =>
        updateLastMessageTs()
      }

      handlers = handlers :+ f
    }

    def createThread(name: String, replaces: PrintStream, install: PrintStream => Unit): Thread = {
      val in  = new PipedInputStream
      val out = new PipedOutputStream(in)
      install(new PrintStream(out))
      outputInspectThread(name, in, replaces, handlers)
    }

    if (handlers.nonEmpty) {
      threads = threads ++ Seq(
        createThread("inspect-out", System.out, System.setOut),
        createThread("inspect-err", System.err, System.setErr)
      )

      threads.foreach(_.start())
    }
  }
}