package sbt
package internal

import java.net.URLClassLoader
import java.nio.file.{ Files, Path, Paths }
import java.io.File
import java.util.Properties
import sbt.internal.worker.{ ConsoleConfig, WorkerMain }
import sbt.io.IO
import sjsonnew.support.scalajson.unsafe.{ Converter, CompactPrinter }
import sbt.internal.util.{ Terminal as ITerminal }

// Used for currentClasspath
private[sbt] class ForkWorker

private[sbt] object ForkWorker:
  def console(config: ConsoleConfig): Int =
    IO.withTemporaryDirectory: tempDir =>
      import sbt.internal.worker.codec.JsonProtocol.given
      val json = Converter.toJson[ConsoleConfig](config).get
      // change to the following to debug params.json
      // val params = Paths.get("/tmp/params.json")
      val params = tempDir.toPath().resolve("params.json")
      IO.write(params.toFile(), CompactPrinter(json))
      runWithCurrentClasspath(
        mainClass = classOf[ConsoleMain].getCanonicalName,
        args = List(s"@$params"),
      )

  def runWithCurrentClasspath(mainClass: String, args: List[String]): Int =
    run(
      mainClass = mainClass,
      classpath = currentClasspath,
      args = args,
    )

  /**
   * Runs arbitrary mainClass with arbitrary classpath using a worker.
   */
  def run(mainClass: String, classpath: List[Path], args: List[String]): Int =
    IO.withTemporaryDirectory: tempDir =>
      val props = Properties()
      props.setProperty("mainClass", mainClass)
      classpath.zipWithIndex.foreach { case (item, idx) =>
        props.setProperty(f"classpath$idx%04d", item.toString())
      }
      args.zipWithIndex.foreach { case (item, idx) =>
        props.setProperty(f"args$idx%04d", item.toString())
      }
      // change to the following to debug 0.params
      // val propPath = Paths.get("/tmp/0.param")
      val propPath = tempDir.toPath().resolve("0.params")
      val out = Files.newOutputStream(propPath)
      try {
        props.store(out, "")
      } finally out.close()
      val fullCp = Seq(
        IO.classLocationPath(classOf[WorkerMain]),
        IO.classLocationPath(classOf[jline.Terminal]),
        IO.classLocationPath(classOf[org.jline.terminal.impl.jni.JniNativePty]),
        IO.classLocationPath(classOf[org.jline.terminal.Terminal]),
        IO.classLocationPath(classOf[org.jline.reader.History]),
        IO.classLocationPath(classOf[org.jline.nativ.JLineLibrary]),
        IO.classLocationPath(classOf[org.jline.builtins.Source]),
        IO.classLocationPath(classOf[org.jline.utils.InfoCmp]),
        IO.classLocationPath(classOf[org.jline.style.StyleFactory]),
        IO.classLocationPath(classOf[org.jline.keymap.KeyMap[?]]),
      )
      val javaArgs = Seq(
        "-classpath",
        fullCp.mkString(File.pathSeparator),
        classOf[WorkerMain].getCanonicalName,
        "run",
        propPath.toString()
      )
      val forkOptions = ForkOptions()
        .withEnvVars(sys.env)
        .withConnectInput(true)
        .withRunJVMOptions(Vector("-Dsbt.io.virtual=false"))
      val terminal = ITerminal.console
      terminal.restore()
      val exitCode = Fork.java(
        config = forkOptions,
        arguments = javaArgs,
      )
      terminal.restore()
      exitCode

  def currentClasspath: List[Path] =
    val cl = classOf[ForkWorker].getClassLoader() match
      case cl: URLClassLoader => cl
    val urls = cl.getURLs().toList
    urls.map((u) => Paths.get(u.toURI())) ++ Vector(
      IO.classLocationPath(classOf[xsbti.compile.ScalaInstance]),
      IO.classLocationPath(classOf[xsbti.Logger]),
    )
end ForkWorker
