package coursier

import java.io.{ File, Writer }
import java.sql.Timestamp
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable.ArrayBuffer
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

object TermDisplay {
  def defaultFallbackMode: Boolean = {
    val env0 = sys.env.get("COURSIER_PROGRESS").map(_.toLowerCase).collect {
      case "true"  | "enable"  | "1" => true
      case "false" | "disable" | "0" => false
    }
    def compatibilityEnv = sys.env.get("COURSIER_NO_TERM").nonEmpty

    def nonInteractive = System.console() == null

    def insideEmacs = sys.env.contains("INSIDE_EMACS")
    def ci = sys.env.contains("CI")

    val env = env0.getOrElse(compatibilityEnv)

    env || nonInteractive || insideEmacs || ci
  }


  private sealed abstract class Info extends Product with Serializable {
    def fraction: Option[Double]
    def display(): String
  }

  private case class DownloadInfo(
    downloaded: Long,
    previouslyDownloaded: Long,
    length: Option[Long],
    startTime: Long,
    updateCheck: Boolean
  ) extends Info {
    /** 0.0 to 1.0 */
    def fraction: Option[Double] = length.map(downloaded.toDouble / _)
    /** Byte / s */
    def rate(): Option[Double] = {
      val currentTime = System.currentTimeMillis()
      if (currentTime > startTime)
        Some((downloaded - previouslyDownloaded).toDouble / (System.currentTimeMillis() - startTime) * 1000.0)
      else
        None
    }

    // Scala version of http://stackoverflow.com/questions/3758606/how-to-convert-byte-size-into-human-readable-format-in-java/3758880#3758880
    private def byteCount(bytes: Long, si: Boolean = false) = {
      val unit = if (si) 1000 else 1024
      if (bytes < unit)
        bytes + " B"
      else {
        val prefixes = if (si) "kMGTPE" else "KMGTPE"
        val exp = (math.log(bytes) / math.log(unit)).toInt min prefixes.length
        val pre = prefixes.charAt(exp - 1) + (if (si) "" else "i")
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

  private val format =
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  private def formatTimestamp(ts: Long): String =
    format.format(new Timestamp(ts))

  private case class CheckUpdateInfo(
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long],
    isDone: Boolean
  ) extends Info {
    def fraction = None
    def display(): String = {
      if (isDone)
        (currentTimeOpt, remoteTimeOpt) match {
          case (Some(current), Some(remote)) =>
            if (current < remote)
              s"Updated since ${formatTimestamp(current)} (${formatTimestamp(remote)})"
            else if (current == remote)
              s"No new update since ${formatTimestamp(current)}"
            else
              s"Warning: local copy newer than remote one (${formatTimestamp(current)} > ${formatTimestamp(remote)})"
          case (Some(_), None) =>
            // FIXME Likely a 404 Not found, that should be taken into account by the cache
            "No modified time in response"
          case (None, Some(remote)) =>
            s"Last update: ${formatTimestamp(remote)}"
          case (None, None) =>
            "" // ???
        }
      else
        currentTimeOpt match {
          case Some(current) =>
            s"Checking for updates since ${formatTimestamp(current)}"
          case None =>
            "" // ???
        }
    }
  }

  private class UpdateDisplayRunnable(
    out: Writer,
    width: Int,
    fallbackMode: Boolean
  ) extends Runnable {

    import Terminal.Ansi

    private var currentHeight = 0

    private val needsUpdate = new AtomicBoolean(false)

    def update(): Unit =
      needsUpdate.set(true)

    private val downloads = new ArrayBuffer[String]
    private val doneQueue = new ArrayBuffer[(String, Info)]
    val infos = new ConcurrentHashMap[String, Info]

    def newEntry(
      url: String,
      info: Info,
      fallbackMessage: => String
    ): Unit = {
      assert(!infos.containsKey(url))
      val prev = infos.putIfAbsent(url, info)
      assert(prev == null)

      if (fallbackMode) {
        // FIXME What about concurrent accesses to out from the thread above?
        out.write(fallbackMessage)
        out.flush()
      }

      downloads.synchronized {
        downloads.append(url)
      }

      update()
    }

    def removeEntry(
      url: String,
      success: Boolean,
      fallbackMessage: => String
    )(
      update0: Info => Info
    ): Unit = {
      downloads.synchronized {
        downloads -= url

        val info = infos.remove(url)
        assert(info != null)

        if (success)
          doneQueue += (url -> update0(info))
      }

      if (fallbackMode && success) {
        // FIXME What about concurrent accesses to out from the thread above?
        out.write(fallbackMessage)
        out.flush()
      }

      update()
    }

    private def reflowed(url: String, info: Info) = {
      val extra = info match {
        case downloadInfo: DownloadInfo =>
          val pctOpt = downloadInfo.fraction.map(100.0 * _)

          if (downloadInfo.length.isEmpty && downloadInfo.downloaded == 0L)
            ""
          else
            s"(${pctOpt.map(pct => f"$pct%.2f %%, ").mkString}${downloadInfo.downloaded}${downloadInfo.length.map(" / " + _).mkString})"

        case updateInfo: CheckUpdateInfo =>
          "Checking for updates"
      }

      val baseExtraWidth = width / 5

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

    private def truncatedPrintln(s: String): Unit = {

      out.clearLine(2)

      if (s.length <= width)
        out.write(s + "\n")
      else
        out.write(s.take(width - 1) + "…\n")
    }

    private def updateDisplay(): Unit =
      if (needsUpdate.getAndSet(false)) {
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
          out.clearLine(2)
          out.write(s"  ${info.display()}\n")
        }

        val displayedCount = (done0 ++ downloads0).length

        if (displayedCount < currentHeight) {
          for (_ <- 1 to 2; _ <- displayedCount until currentHeight) {
            out.clearLine(2)
            out.down(1)
          }

          for (_ <- displayedCount until currentHeight)
            out.up(2)
        }

        for (_ <- downloads0.indices)
          out.up(2)

        out.left(10000)

        out.flush()

        currentHeight = downloads0.length
      }

    def cleanDisplay(): Unit = {
      for (_ <- 1 to 2; _ <- 0 until currentHeight) {
        out.clearLine(2)
        out.down(1)
      }
      for (_ <- 0 until currentHeight)
        out.up(2)
    }

    private var previous = Set.empty[String]

    private def fallbackDisplay(): Unit = {

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

      previous = previous ++ downloads0.map { case (url, _) => url }
    }

    def init(): Unit =
      if (!fallbackMode)
        out.clearLine(2)

    def run(): Unit =
      if (fallbackMode)
        fallbackDisplay()
      else
        updateDisplay()
  }

}

class TermDisplay(
  out: Writer,
  val fallbackMode: Boolean = TermDisplay.defaultFallbackMode
) extends Cache.Logger {

  import TermDisplay._

  private var updateRunnableOpt = Option.empty[UpdateDisplayRunnable]
  private val scheduler = Executors.newSingleThreadScheduledExecutor(
    new ThreadFactory {
      val defaultThreadFactory = Executors.defaultThreadFactory()
      def newThread(r: Runnable) = {
        val t = defaultThreadFactory.newThread(r)
        t.setDaemon(true)
        t.setName("progress-bar")
        t
      }
    }
  )

  private def updateRunnable = updateRunnableOpt.getOrElse {
    throw new Exception("Uninitialized TermDisplay")
  }

  val defaultWidth = 80

  lazy val (width, fallbackMode0) = Terminal.consoleDim("cols") match {
    case Some(w) =>
      (w, fallbackMode)
    case None =>
      (defaultWidth, true)
  }

  lazy val refreshInterval =
    if (fallbackMode0)
      1000L
    else
      1000L / 60

  def init(): Unit = {
    updateRunnableOpt = Some(new UpdateDisplayRunnable(out, width, fallbackMode0))

    updateRunnable.init()
    scheduler.scheduleAtFixedRate(updateRunnable, 0L, refreshInterval, TimeUnit.MILLISECONDS)
  }

  def stop(): Unit = {
    scheduler.shutdown()
    scheduler.awaitTermination(refreshInterval, TimeUnit.MILLISECONDS)
    updateRunnable.cleanDisplay()
  }

  override def downloadingArtifact(url: String, file: File): Unit =
    updateRunnable.newEntry(
      url,
      DownloadInfo(0L, 0L, None, System.currentTimeMillis(), updateCheck = false),
      s"Downloading $url\n"
    )

  override def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long): Unit = {
    val info = updateRunnable.infos.get(url)
    assert(info != null)
    val newInfo = info match {
      case info0: DownloadInfo =>
        info0.copy(length = Some(totalLength), previouslyDownloaded = alreadyDownloaded)
      case _ =>
        throw new Exception(s"Incoherent display state for $url")
    }
    updateRunnable.infos.put(url, newInfo)

    updateRunnable.update()
  }
  override def downloadProgress(url: String, downloaded: Long): Unit = {
    val info = updateRunnable.infos.get(url)
    assert(info != null)
    val newInfo = info match {
      case info0: DownloadInfo =>
        info0.copy(downloaded = downloaded)
      case _ =>
        throw new Exception(s"Incoherent display state for $url")
    }
    updateRunnable.infos.put(url, newInfo)

    updateRunnable.update()
  }

  override def downloadedArtifact(url: String, success: Boolean): Unit =
    updateRunnable.removeEntry(url, success, s"Downloaded $url\n")(x => x)

  override def checkingUpdates(url: String, currentTimeOpt: Option[Long]): Unit =
    updateRunnable.newEntry(
      url,
      CheckUpdateInfo(currentTimeOpt, None, isDone = false),
      s"Checking $url\n"
    )

  override def checkingUpdatesResult(
    url: String,
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long]
  ): Unit = {
    // Not keeping a message on-screen if a download should happen next
    // so that the corresponding URL doesn't appear twice
    val newUpdate = remoteTimeOpt.exists { remoteTime =>
      currentTimeOpt.forall { currentTime =>
        currentTime < remoteTime
      }
    }

    updateRunnable.removeEntry(url, !newUpdate, s"Checked $url\n") {
      case info: CheckUpdateInfo =>
        info.copy(remoteTimeOpt = remoteTimeOpt, isDone = true)
      case _ =>
        throw new Exception(s"Incoherent display state for $url")
    }
  }

}
