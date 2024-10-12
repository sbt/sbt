package sbt.internal.worker

import java.io.PrintStream
import java.nio.file.{ Files, Paths }
import sbt.Run
import sbt.util.Logger
import sbt.internal.inc.classpath.ClasspathUtil
import sbt.internal.util.Terminal
import sbt.io.{ IO, Using }

object WorkRun:
  def apply(params: GeneralParams, stdout: String => Unit): Unit =
    val classpath = params.classpath.map: (classpath) =>
      val p = Paths.get(classpath.path)
      require(Files.exists(p), s"$p does not exist")
      p
    val runner = Run(
      newLoader = (cp) => ClasspathUtil.toLoader(cp),
      trapExit = false,
    )
    val output = withStdout:
      runner
        .run(
          mainClass = params.main_class,
          classpath = classpath,
          options = params.args,
          log = Logger.Null,
        )
        .get
      ()
    stdout(output)

  def withStdout[A1](f: => A1): String =
    IO.withTemporaryFile("sbt-stdout", ".log"): (tmp) =>
      val result = Using.fileOutputStream(append = true)(tmp): (stream) =>
        val out = PrintStream(stream)
        Terminal.withOut(out):
          f
        out.flush()
        IO.read(tmp)
      result
end WorkRun
