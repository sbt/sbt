package coursier.cli

import java.io.{ BufferedReader, File, InputStream, InputStreamReader, OutputStream }
import java.nio.file.{ Files, Paths }

import caseapp._

import scala.util.control.NonFatal

@CommandName("spark-submit")
case class SparkSubmit(
  @Recurse
    options: SparkSubmitOptions
) extends App with ExtraArgsApp {

  val helper = new Helper(options.common, remainingArgs)

  val jars = helper.fetch(sources = false, javadoc = false)


  val sparkHome =
    if (options.sparkHome.isEmpty)
      sys.env.getOrElse(
        "SPARK_HOME", {
          Console.err.println("Error: SPARK_HOME not set and the --spark-home option not given a value.")
          sys.exit(1)
        }
      )
    else
      options.sparkHome

  val sparkAssembly = {
    // TODO Make this more reliable (assemblies can be found in other directories I think, this
    // must be fine with spark 2.10 too, ...)
    val dir = new File(sparkHome + "/assembly/target/scala-2.11")
    Option(dir.listFiles()).getOrElse(Array.empty).filter { f =>
      f.isFile && f.getName.endsWith(".jar")
    } match {
      case Array(assemblyFile) =>
        assemblyFile.getAbsolutePath
      case Array() =>
        throw new Exception(s"No spark assembly found under $dir")
      case jars =>
        throw new Exception(s"Found several JARs under $dir")
    }
  }

  val libManaged = {
    val dir = new File(sparkHome + "/lib_managed/jars")
    if (dir.isDirectory) {
      dir.listFiles().toSeq.map(_.getAbsolutePath)
    } else
      Nil
  }

  val yarnConfOpt = sys.env.get("YARN_CONF_DIR").filter(_.nonEmpty)

  for (yarnConf <- yarnConfOpt if !new File(yarnConf).isDirectory)
    throw new Exception(s"Error: YARN conf path ($yarnConf) is not a directory or doesn't exist.")

  val cp = Seq(
    sparkHome + "/conf",
    sparkAssembly
  ) ++ libManaged ++ yarnConfOpt.toSeq

  val idx = extraArgs.indexOf("--")
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

  val (check, extraJars) = jars.partition(_.getAbsolutePath == mainJar)

  if (check.isEmpty)
    Console.err.println(
      s"Warning: cannot find back $mainJar among the dependencies JARs (likely a coursier bug)"
    )

  val extraJarsOptions =
    if (extraJars.isEmpty)
      Nil
    else
      Seq("--jars", extraJars.mkString(","))

  val mainClassOptions = Seq("--class", mainClass)

  val sparkSubmitOptions = sparkOpts ++ extraJarsOptions ++ mainClassOptions ++
    Seq(mainJar) ++ jobArgs

  val cmd = Seq(
    "java",
    "-cp",
    cp.mkString(File.pathSeparator),
    "org.apache.spark.deploy.SparkSubmit"
  ) ++ sparkSubmitOptions

  object YarnAppId {
    val Pattern = ".*Application report for ([^ ]+) .*".r

    val fileOpt = Some(options.yarnIdFile).filter(_.nonEmpty)

    @volatile var written = false
    val lock = new AnyRef
    def handleMessage(s: String): Unit =
      if (!written)
        s match {
          case Pattern(id) =>
            lock.synchronized {
              if (!written) {
                println(s"Detected YARN app ID $id")
                for (writeAppIdTo <- fileOpt) {
                  val path = Paths.get(writeAppIdTo)
                  Option(path.getParent).foreach(_.toFile.mkdirs())
                  Files.write(path, id.getBytes("UTF-8"))
                }
                written = true
              }
            }
          case _ =>
        }
  }

  object IdleChecker {

    @volatile var lastMessageTs = -1L

    def updateLastMessageTs() = {
      lastMessageTs = System.currentTimeMillis()
    }

    val checkThreadOpt =
      if (options.maxIdleTime > 0) {

        val checkThread = new Thread {
          override def run() =
            try {
              while (true) {
                lastMessageTs = -1L
                Thread.sleep(options.maxIdleTime * 1000L)
                if (lastMessageTs < 0) {
                  Console.err.println(s"No output from spark-submit for more than ${options.maxIdleTime} s, exiting")
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

        Some(checkThread)
      } else
        None
  }

  Console.err.println(s"Running command:\n${cmd.map("  "+_).mkString("\n")}\n")

  val process = new ProcessBuilder()
    .command(cmd: _*)
    .redirectErrorStream(true) // merges error stream into output stream
    .start()

  def pipeThread(from: InputStream, to: OutputStream) = {
    val t = new Thread {
      override def run() = {
        val in = new BufferedReader(new InputStreamReader(from))
        var line: String = null
        while ({
          line = in.readLine()
          line != null
        }) {
          if (options.maxIdleTime > 0)
            IdleChecker.updateLastMessageTs()

          to.write((line + "\n").getBytes("UTF-8"))

          if (YarnAppId.fileOpt.nonEmpty)
            try YarnAppId.handleMessage(line)
            catch {
              case NonFatal(_) =>
            }
        }
      }
    }

    t.setName("pipe-output")
    t.setDaemon(true)

    t
  }

  val is = process.getInputStream

  val isPipeThread = pipeThread(is, System.out)

  IdleChecker.checkThreadOpt.foreach(_.start())
  isPipeThread.start()

  val exitValue = process.waitFor()

  sys.exit(exitValue)

}
