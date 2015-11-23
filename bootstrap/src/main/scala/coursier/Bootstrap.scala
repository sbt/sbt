package coursier

import java.io.{ ByteArrayOutputStream, InputStream, File }
import java.net.{ URI, URLClassLoader }
import java.nio.file.Files
import java.util.concurrent.{ Executors, ThreadFactory }

import scala.concurrent.duration.Duration
import scala.concurrent.{ ExecutionContext, Future, Await }

import scala.util.{ Try, Success, Failure }

object Bootstrap extends App {

  val concurrentDownloadCount = 6
  val threadFactory = new ThreadFactory {
    // from scalaz Strategy.DefaultDaemonThreadFactory
    val defaultThreadFactory = Executors.defaultThreadFactory()
    def newThread(r: Runnable) = {
      val t = defaultThreadFactory.newThread(r)
      t.setDaemon(true)
      t
    }
  }
  val defaultPool = Executors.newFixedThreadPool(concurrentDownloadCount, threadFactory)
  implicit val ec = ExecutionContext.fromExecutorService(defaultPool)

  private def readFullySync(is: InputStream) = {
    val buffer = new ByteArrayOutputStream()
    val data = Array.ofDim[Byte](16384)

    var nRead = is.read(data, 0, data.length)
    while (nRead != -1) {
      buffer.write(data, 0, nRead)
      nRead = is.read(data, 0, data.length)
    }

    buffer.flush()
    buffer.toByteArray
  }

  private def errPrintln(s: String): Unit =
    Console.err.println(s)

  private def exit(msg: String = ""): Nothing = {
    if (msg.nonEmpty)
      errPrintln(msg)
    sys.exit(255)
  }

  val (prependClasspath, mainClass0, jarDir0, remainingArgs) = args match {
    case Array("-B", mainClass0, jarDir0, remainingArgs @ _*) =>
      (true, mainClass0, jarDir0, remainingArgs)
    case Array(mainClass0, jarDir0, remainingArgs @ _*) =>
      (false, mainClass0, jarDir0, remainingArgs)
    case _ =>
      exit("Usage: bootstrap main-class JAR-directory JAR-URLs...")
  }

  val jarDir = new File(jarDir0)

  if (jarDir.exists()) {
    if (!jarDir.isDirectory)
      exit(s"Error: $jarDir0 is not a directory")
  } else if (!jarDir.mkdirs())
      errPrintln(s"Warning: cannot create $jarDir0, continuing anyway.")

  val splitIdx = remainingArgs.indexOf("--")
  val (jarStrUrls, userArgs) =
    if (splitIdx < 0)
      (remainingArgs, Nil)
    else
      (remainingArgs.take(splitIdx), remainingArgs.drop(splitIdx + 1))

  val tryUrls = jarStrUrls.map(urlStr => urlStr -> Try(URI.create(urlStr).toURL))

  val failedUrls = tryUrls.collect {
    case (strUrl, Failure(t)) => strUrl -> t
  }
  if (failedUrls.nonEmpty)
    exit(
      s"Error parsing ${failedUrls.length} URL(s):\n" +
      failedUrls.map { case (s, t) => s"$s: ${t.getMessage}" }.mkString("\n")
    )

  val jarUrls = tryUrls.collect {
    case (_, Success(url)) => url
  }

  val jarLocalUrlFutures = jarUrls.map { url =>
    if (url.getProtocol == "file")
      Future.successful(url)
    else
      Future {
        val path = url.getPath
        val idx = path.lastIndexOf('/')
        // FIXME Add other components in path to prevent conflicts?
        val fileName = path.drop(idx + 1)
        val dest = new File(jarDir, fileName)

        // FIXME If dest exists, do a HEAD request and check that its size or last modified time is OK?

        if (!dest.exists()) {
          Console.err.println(s"Downloading $url")
          try {
            val conn = url.openConnection()
            val lastModified = conn.getLastModified
            val s = conn.getInputStream
            val b = readFullySync(s)
            Files.write(dest.toPath, b)
            dest.setLastModified(lastModified)
          } catch { case e: Exception =>
            Console.err.println(s"Error while downloading $url: ${e.getMessage}, ignoring it")
          }
        }

        dest.toURI.toURL
      }
  }

  val jarLocalUrls = Await.result(Future.sequence(jarLocalUrlFutures), Duration.Inf)

  val thread = Thread.currentThread()
  val parentClassLoader = thread.getContextClassLoader

  val classLoader = new URLClassLoader(jarLocalUrls.toArray, parentClassLoader)

  val mainClass =
    try classLoader.loadClass(mainClass0)
    catch { case e: ClassNotFoundException =>
      exit(s"Error: class $mainClass0 not found")
    }

  val mainMethod =
    try mainClass.getMethod("main", classOf[Array[String]])
    catch { case e: NoSuchMethodException =>
      exit(s"Error: main method not found in class $mainClass0")
    }

  val userArgs0 =
    if (prependClasspath)
      jarLocalUrls.flatMap { url =>
        assert(url.getProtocol == "file")
        Seq("-B", url.getPath)
      } ++ userArgs
    else
      userArgs

  thread.setContextClassLoader(classLoader)
  try mainMethod.invoke(null, userArgs0.toArray)
  finally {
    thread.setContextClassLoader(parentClassLoader)
  }

}