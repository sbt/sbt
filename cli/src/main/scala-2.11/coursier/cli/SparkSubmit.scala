package coursier.cli

import java.io.{PrintStream, BufferedReader, File, PipedInputStream, PipedOutputStream, InputStream, InputStreamReader}
import java.net.URLClassLoader

import caseapp._

import coursier.{ Attributes, Dependency }
import coursier.cli.spark.{ Assembly, Submit }
import coursier.internal.FileUtil
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
final case class SparkSubmit(
  @Recurse
    options: SparkSubmitOptions
) extends App with ExtraArgsApp {

  val rawExtraJars = options.extraJars.map(new File(_))

  val extraDirs = rawExtraJars.filter(_.isDirectory)
  if (extraDirs.nonEmpty) {
    Console.err.println(s"Error: directories not allowed in extra job JARs.")
    Console.err.println(extraDirs.map("  " + _).mkString("\n"))
    sys.exit(1)
  }

  val helper: Helper = new Helper(
    options.common,
    remainingArgs,
    extraJars = rawExtraJars
  )
  val jars =
    helper.fetch(
      sources = false,
      javadoc = false,
      artifactTypes = options.artifactOptions.artifactTypes(sources = false, javadoc = false)
    ) ++ options.extraJars.map(new File(_))

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

  val (sparkYarnExtraConf, sparkBaseJars) =
    if (!options.autoAssembly || sparkVersion.startsWith("2.")) {

      val assemblyJars = Assembly.sparkJars(
        scalaVersion,
        sparkVersion,
        options.yarnVersion,
        options.defaultAssemblyDependencies.getOrElse(options.autoAssembly),
        options.assemblyDependencies.flatMap(_.split(",")).filter(_.nonEmpty),
        options.common
      )

      val extraConf =
        if (options.autoAssembly && sparkVersion.startsWith("2."))
          Seq(
            "spark.yarn.jars" -> assemblyJars.map(_.getAbsolutePath).mkString(",")
          )
        else
          Nil

      (extraConf, assemblyJars)
    } else {

      val assemblyAndJarsOrError = Assembly.spark(
        scalaVersion,
        sparkVersion,
        options.yarnVersion,
        options.defaultAssemblyDependencies.getOrElse(true),
        options.assemblyDependencies.flatMap(_.split(",")).filter(_.nonEmpty),
        options.common
      )

      val (assembly, assemblyJars) = assemblyAndJarsOrError match {
        case Left(err) =>
          Console.err.println(s"Cannot get spark assembly: $err")
          sys.exit(1)
        case Right(res) => res
      }

      val extraConf = Seq(
        "spark.yarn.jar" -> assembly.getAbsolutePath
      )

      (extraConf, assemblyJars)
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

  val extraJars = extraJars0.filterNot(sparkBaseJars.toSet)

  if (check.isEmpty)
    Console.err.println(
      s"Warning: cannot find back $mainJar among the dependencies JARs (likely a coursier bug)"
    )

  val extraSparkOpts = sparkYarnExtraConf.flatMap {
    case (k, v) => Seq(
      "--conf", s"$k=$v"
    )
  }

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
    options.submitDependencies.flatMap(_.split(",")).filter(_.nonEmpty),
    options.artifactOptions.artifactTypes(sources = false, javadoc = false),
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
                  Option(yarnAppFile.getParentFile).foreach(_.mkdirs())
                  FileUtil.write(yarnAppFile, id.getBytes("UTF-8"))
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
