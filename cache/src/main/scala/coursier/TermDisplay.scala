package coursier

import java.io.{ File, Writer }
import java.sql.Timestamp
import java.util.concurrent._
import java.util.concurrent.atomic.AtomicBoolean

import scala.collection.mutable.ArrayBuffer

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

    val env = env0.fold(compatibilityEnv)(!_)

    env || nonInteractive || insideEmacs || ci
  }


  private sealed abstract class Info extends Product with Serializable {
    def fraction: Option[Double]
    def display(isDone: Boolean): String
    def watching: Boolean
  }

  private final case class DownloadInfo(
    downloaded: Long,
    previouslyDownloaded: Long,
    length: Option[Long],
    startTime: Long,
    updateCheck: Boolean,
    watching: Boolean
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

    def display(isDone: Boolean): String = {

      val actualFraction = fraction
        .orElse(if (isDone) Some(1.0) else None)
        .orElse(if (downloaded == 0L) Some(0.0) else None)

      val start =
        actualFraction match {
          case None =>
            val elem = if (watching) "." else "?"
            s"       [     $elem    ] "
          case Some(frac) =>
            val elem = if (watching) "." else "#"

            val decile = (10.0 * frac).toInt
            assert(decile >= 0)
            assert(decile <= 10)

            f"${100.0 * frac}%5.1f%%" +
              " [" + (elem * decile) + (" " * (10 - decile)) + "] "
        }

      start +
        byteCount(downloaded) +
        rate().fold("")(r => s" (${byteCount(r.toLong)} / s)")
    }
  }

  private val format =
    new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss")
  private def formatTimestamp(ts: Long): String =
    format.format(new Timestamp(ts))

  private final case class CheckUpdateInfo(
    currentTimeOpt: Option[Long],
    remoteTimeOpt: Option[Long],
    isDone: Boolean
  ) extends Info {
    def watching = false
    def fraction = None
    def display(isDone: Boolean): String = {
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
    beforeOutput: => Unit,
    out: Writer,
    width: Int,
    fallbackMode: Boolean
  ) extends Runnable {

    import Terminal.Ansi

    private var currentHeight = 0
    private var printedAnything0 = false

    private var stopped = false

    def printedAnything() = printedAnything0

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
      val inf = downloads.synchronized {
        downloads -= url

        val info = infos.remove(url)
        assert(info != null)

        if (success)
          doneQueue += (url -> update0(info))

        info
      }

      if (fallbackMode && success) {
        // FIXME What about concurrent accesses to out from the thread above?
        out.write((if (inf.watching) "(watching) " else "") + fallbackMessage)
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

        case _: CheckUpdateInfo =>
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
      if (!stopped && needsUpdate.getAndSet(false)) {
        val (done0, downloads0) = downloads.synchronized {
          val q = doneQueue
            .toVector
            .filter {
              case (url, _) =>
                !url.endsWith(".sha1") && !url.endsWith(".md5") && !url.endsWith("/")
            }
            .sortBy { case (url, _) => url }

          doneQueue.clear()

          val dw = downloads
            .toVector
            .map { url => url -> infos.get(url) }
            .sortBy { case (_, info) => - info.fraction.sum }

          (q, dw)
        }

        for (((url, info), isDone) <- done0.iterator.map((_, true)) ++ downloads0.iterator.map((_, false))) {
          assert(info != null, s"Incoherent state ($url)")

          if (!printedAnything0) {
            beforeOutput
            printedAnything0 = true
          }

          truncatedPrintln(url)
          out.clearLine(2)
          out.write(s"  ${info.display(isDone)}\n")
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

    def stop(): Unit = {
      for (_ <- 1 to 2; _ <- 0 until currentHeight) {
        out.clearLine(2)
        out.down(1)
      }
      for (_ <- 0 until currentHeight)
        out.up(2)

      out.flush()

      stopped = true
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
) extends Cache.Logger.Extended {

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

  /***
    *
    * @param beforeOutput: called before any output is printed, iff something else is outputed.
    *                      (That is, if that `TermDisplay` doesn't print any progress,
    *                      `initialMessage` won't be printed either.)
    */
  def init(beforeOutput: => Unit): Unit = {
    updateRunnableOpt = Some(new UpdateDisplayRunnable(beforeOutput, out, width, fallbackMode0))

    updateRunnable.init()
    scheduler.scheduleAtFixedRate(updateRunnable, 0L, refreshInterval, TimeUnit.MILLISECONDS)
  }

  def init(): Unit =
    init(())

  /**
    *
    * @return whether any message was printed by this `TermDisplay`
    */
  def stopDidPrintSomething(): Boolean = {
    scheduler.shutdown()
    scheduler.awaitTermination(2 * refreshInterval, TimeUnit.MILLISECONDS)
    updateRunnable.stop()
    updateRunnable.printedAnything()
  }

  def stop(): Unit =
    stopDidPrintSomething()

  override def downloadingArtifact(url: String, file: File): Unit =
    updateRunnable.newEntry(
      url,
      DownloadInfo(0L, 0L, None, System.currentTimeMillis(), updateCheck = false, watching = false),
      s"Downloading $url\n"
    )

  override def downloadLength(url: String, totalLength: Long, alreadyDownloaded: Long, watching: Boolean): Unit = {
    val info = updateRunnable.infos.get(url)
    assert(info != null)
    val newInfo = info match {
      case info0: DownloadInfo =>
        info0.copy(
          length = Some(totalLength),
          previouslyDownloaded = alreadyDownloaded,
          watching = watching
        )
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
