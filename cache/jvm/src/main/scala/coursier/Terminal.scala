package coursier

import java.io.{File, Writer}

import scala.util.Try

object Terminal {

  // Cut-n-pasted and adapted from
  // https://github.com/lihaoyi/Ammonite/blob/10854e3b8b454a74198058ba258734a17af32023/terminal/src/main/scala/ammonite/terminal/Utils.scala

  private lazy val pathedTput = if (new File("/usr/bin/tput").exists()) "/usr/bin/tput" else "tput"

  def consoleDim(s: String): Option[Int] =
    if (new File("/dev/tty").exists()) {
      import sys.process._
      val nullLog = new ProcessLogger {
        def out(s: => String): Unit = {}
        def err(s: => String): Unit = {}
        def buffer[T](f: => T): T = f
      }
      Try(Process(Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty")).!!(nullLog).trim.toInt).toOption
    } else
      None

  implicit class Ansi(val output: Writer) extends AnyVal {
    private def control(n: Int, c: Char) = output.write(s"\033[" + n + c)

    /**
      * Move up `n` squares
      */
    def up(n: Int): Unit = if (n > 0) control(n, 'A')
    /**
      * Move down `n` squares
      */
    def down(n: Int): Unit = if (n > 0) control(n, 'B')
    /**
      * Move left `n` squares
      */
    def left(n: Int): Unit = if (n > 0) control(n, 'D')

    /**
      * Clear the current line
      *
      * n=0: clear from cursor to end of line
      * n=1: clear from cursor to start of line
      * n=2: clear entire line
      */
    def clearLine(n: Int): Unit = control(n, 'K')
  }

}
