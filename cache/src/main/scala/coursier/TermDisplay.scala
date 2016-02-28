package coursier

import java.io.{File, Writer}
import java.util.concurrent._

import scala.annotation.tailrec
import scala.collection.mutable.ArrayBuffer
import scala.util.Try

object Terminal {

  // Cut-n-pasted and adapted from
  // https://github.com/lihaoyi/Ammonite/blob/10854e3b8b454a74198058ba258734a17af32023/terminal/src/main/scala/ammonite/terminal/Utils.scala

  private lazy val pathedTput = if (new File("/usr/bin/tput").exists()) "/usr/bin/tput" else "tput"

  def consoleDim(s: String): Option[Int] =
    if (new File("/dev/tty").exists()) {
      import sys.process._
      Try(Seq("bash", "-c", s"$pathedTput $s 2> /dev/tty").!!.trim.toInt).toOption
    } else
      None

  class Ansi(val output: Writer) extends AnyVal {
    private def control(n: Int, c: Char) = output.write(s"\033[" + n + c)

    /**
      * Move up `n` squares
      */
    def up(n: Int): Unit = if (n == 0) "" else control(n, 'A')
    /**
      * Move down `n` squares
      */
    def down(n: Int): Unit = if (n == 0) "" else control(n, 'B')
    /**
      * Move left `n` squares
      */
    def left(n: Int): Unit = if (n == 0) "" else control(n, 'D')

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

class TermDisplay(
  out: Writer,
  var fallbackMode: Boolean = sys.env.get("COURSIER_NO_TERM").nonEmpty
) extends Cache.Logger {

  private val ansi = new Terminal.Ansi(out)
  private var width = 80
  private val refreshInterval = 1000 / 60
  private val fallbackRefreshInterval = 1000

  private val lock = new AnyRef
  private var currentHeight = 0
  private val t = new Thread("TermDisplay") {
    override def run() = lock.synchronized {

      val baseExtraWidth = width / 5

      def reflowed(url: String, info: Info) = {
        val pctOpt = info.fraction.map(100.0 * _)
        val extra =
          if (info.length.isEmpty && info.downloaded == 0L)
            ""
          else
            s"(${pctOpt.map(pct => f"$pct%.2f %%, ").mkString}${info.downloaded}${info.length.map(" / " + _).mkString})"

        val total = url.length + 1 + extra.length
        val (url0, extra0) =
          if (total >= width) { // or > ? If equal, does it go down 2 lines?
          val overflow = total - width + 1

            val extra0 =
              if (extra.length > baseExtraWidth)
                extra.take((baseExtraWidth max (extra.length - overflow)) - 1) + "…"
              else
                extra

            val total0 = url.length + 1 + extra0.length
            val overflow0 = total0 - width + 1

            val url0 =
              if (total0 >= width)
                url.take(((width - baseExtraWidth - 1) max (url.length - overflow0)) - 1) + "…"
              else
                url

            (url0, extra0)
          } else
            (url, extra)

        (url0, extra0)
      }

      def truncatedPrintln(s: String): Unit = {

        ansi.clearLine(2)

        if (s.length <= width)
          out.write(s + "\n")
        else
          out.write(s.take(width - 1) + "…\n")
      }

      @tailrec def helper(lineCount: Int): Unit = {
        currentHeight = lineCount

        Option(q.poll(100L, TimeUnit.MILLISECONDS)) match {
          case None => helper(lineCount)
          case Some(Left(())) => // poison pill
          case Some(Right(())) =>
            // update display

            val (done0, downloads0) = downloads.synchronized {
              val q = doneQueue
                .toVector
                .filter {
                  case (url, _) =>
                    !url.endsWith(".sha1") && !url.endsWith(".md5")
                }
                .sortBy { case (url, _) => url }

              doneQueue.clear()

              val dw = downloads
                .toVector
                .map { url => url -> infos.get(url) }
                .sortBy { case (_, info) => - info.fraction.sum }

              (q, dw)
            }

            for ((url, info) <- done0 ++ downloads0) {
              assert(info != null, s"Incoherent state ($url)")

              truncatedPrintln(url)
              ansi.clearLine(2)
              out.write(s"  ${info.display()}\n")
            }

            val displayedCount = (done0 ++ downloads0).length

            if (displayedCount < lineCount) {
              for (_ <- 1 to 2; _ <- displayedCount until lineCount) {
                ansi.clearLine(2)
                ansi.down(1)
              }

              for (_ <- displayedCount until lineCount)
                ansi.up(2)
            }

            for (_ <- downloads0.indices)
              ansi.up(2)

            ansi.left(10000)

            out.flush()
            Thread.sleep(refreshInterval)
            helper(downloads0.length)
        }
      }


      @tailrec def fallbackHelper(previous: Set[String]): Unit =
        Option(q.poll(100L, TimeUnit.MILLISECONDS)) match {
          case None => fallbackHelper(previous)
          case Some(Left(())) => // poison pill
          case Some(Right(())) =>
            val downloads0 = downloads.synchronized {
              downloads
                .toVector
                .map { url => url -> infos.get(url) }
                .sortBy { case (_, info) => - info.fraction.sum }
            }

            var displayedSomething = false
            for ((url, info) <- downloads0 if previous(url)) {
              assert(info != null, s"Incoherent state ($url)")

              val (url0, extra0) = reflowed(url, info)

              displayedSomething = true
              out.write(s"$url0 $extra0\n")
            }

            if (displayedSomething)
              out.write("\n")

            out.flush()
            Thread.sleep(fallbackRefreshInterval)
            fallbackHelper(previous ++ downloads0.map { case (url, _) => url })
          }

      if (fallbackMode)
        fallbackHelper(Set.empty)
      else
        helper(0)
    }
  }

  t.setDaemon(true)

  def init(): Unit = {
    Terminal.consoleDim("cols") match {
      case Some(cols) =>
        width = cols
        ansi.clearLine(2)
      case None =>
        fallbackMode = true
    }

    t.start()
  }

  def stop(): Unit = {
    for (_ <- 1 to 2; _ <- 0 until currentHeight) {
      ansi.clearLine(2)
      ansi.down(1)
    }
    for (_ <- 0 until currentHeight) {
      ansi.up(2)
    }
    q.put(Left(()))
    lock.synchronized(())
  }

  private case class Info(downloaded: Long, length: Option[Long], startTime: Long) {
    /** 0.0 to 1.0 */
    def fraction: Option[Double] = length.map(downloaded.toDouble / _)
    /** Byte / s */
    def rate(): Option[Double] = {
      val currentTime = System.currentTimeMillis()
      if (currentTime > startTime)
        Some(downloaded.toDouble / (System.currentTimeMillis() - startTime) * 1000.0)
      else
        None
    }

    // Scala version of http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java/3758880#3758880
    private def byteCount(bytes: Long, si: Boolean = false) = {
      val unit = if (si) 1000 else 1024
      if (bytes < unit)
        bytes + " B"
      else {
        val exp = (math.log(bytes) / math.log(unit)).toInt
        val pre = (if (si) "kMGTPE" else "KMGTPE").charAt(exp - 1) + (if (si) "" else "i")
        f"${bytes / math.pow(unit, exp)}%.1f ${pre}B"
      }
    }

    def display(): String = {
      val decile = (10.0 * fraction.getOrElse(0.0)).toInt
      assert(decile >= 0)
      assert(decile <= 10)

      fraction.fold(" " * 6)(p => f"${100.0 * p}%5.1f%%") +
        " [" + ("#" * decile) + (" " * (10 - decile)) + "] " +
        byteCount(downloaded) +
        rate().fold("")(r => s" (${byteCount(r.toLong)} / s)")
    }
  }

  private val downloads = new ArrayBuffer[String]
  private val doneQueue = new ArrayBuffer[(String, Info)]
  private val infos = new ConcurrentHashMap[String, Info]

  private val q = new LinkedBlockingDeque[Either[Unit, Unit]]
  def update(): Unit = {
    if (q.size() == 0)
      q.put(Right(()))
  }

  override def downloadingArtifact(url: String, file: File): Unit = {
    assert(!infos.containsKey(url))
    val prev = infos.putIfAbsent(url, Info(0L, None, System.currentTimeMillis()))
    assert(prev == null)

    if (fallbackMode) {
      // FIXME What about concurrent accesses to out from the thread above?
      out.write(s"Downloading $url\n")
      out.flush()
    }

    downloads.synchronized {
      downloads.append(url)
    }

    update()
  }
  override def downloadLength(url: String, length: Long): Unit = {
    val info = infos.get(url)
    assert(info != null)
    val newInfo = info.copy(length = Some(length))
    infos.put(url, newInfo)

    update()
  }
  override def downloadProgress(url: String, downloaded: Long): Unit = {
    val info = infos.get(url)
    assert(info != null)
    val newInfo = info.copy(downloaded = downloaded)
    infos.put(url, newInfo)

    update()
  }
  override def downloadedArtifact(url: String, success: Boolean): Unit = {
    downloads.synchronized {
      downloads -= url
      if (success)
        doneQueue += (url -> infos.get(url))
    }

    if (fallbackMode && success) {
      // FIXME What about concurrent accesses to out from the thread above?
      out.write(s"Downloaded $url\n")
      out.flush()
    }

    val info = infos.remove(url)
    assert(info != null)

    update()
  }

}
