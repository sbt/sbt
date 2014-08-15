package xsbt
package boot

import java.io.File
import scala.util.control.NonFatal
import java.net.URI
import java.io.IOException
import Pre._
import scala.annotation.tailrec

/** A wrapper around 'raw' static methods to meet the sbt application interface. */
class ServerApplication private (provider: xsbti.AppProvider) extends xsbti.AppMain {
  import ServerApplication._

  override def run(configuration: xsbti.AppConfiguration): xsbti.MainResult = {
    val serverMain = provider.entryPoint.asSubclass(ServerMainClass).newInstance
    val server = serverMain.start(configuration)
    System.out.println(s"${SERVER_SYNCH_TEXT}${server.uri}")
    server.awaitTermination()
  }
}
/** An object that lets us detect compatible "plain" applications and launch them reflectively. */
object ServerApplication {
  val SERVER_SYNCH_TEXT = "[SERVER-URI]"
  val ServerMainClass = classOf[xsbti.ServerMain]
  // TODO - We should also adapt friendly static methods into servers, perhaps...
  // We could even structurally type things that have a uri + awaitTermination method...
  def isServerApplication(clazz: Class[_]): Boolean =
    ServerMainClass.isAssignableFrom(clazz)
  def apply(provider: xsbti.AppProvider): xsbti.AppMain =
    new ServerApplication(provider)

}
object ServerLocator {
  // TODO - Probably want to drop this to reduce classfile size
  private def locked[U](file: File)(f: => U): U = {
    Locks(file, new java.util.concurrent.Callable[U] {
      def call(): U = f
    })
  }
  // We use the lock file they give us to write the server info.  However,
  // it seems we cannot both use the server info file for locking *and*
  // read from it successfully.  Locking seems to blank the file. SO, we create
  // another file near the info file to lock.a
  def makeLockFile(f: File): File =
    new File(f.getParentFile, s"${f.getName}.lock")
  // Launch the process and read the port...
  def locate(currentDirectory: File, config: LaunchConfiguration): URI =
    config.serverConfig match {
      case None => sys.error("No server lock file configured.  Cannot locate server.")
      case Some(sc) => locked(makeLockFile(sc.lockFile)) {
        readProperties(sc.lockFile) match {
          case Some(uri) if isReachable(uri) => uri
          case _ =>
            val uri = ServerLauncher.startServer(currentDirectory, config)
            writeProperties(sc.lockFile, uri)
            uri
        }
      }
    }

  private val SERVER_URI_PROPERTY = "server.uri"
  def readProperties(f: File): Option[java.net.URI] = {
    try {
      val props = Pre.readProperties(f)
      props.getProperty(SERVER_URI_PROPERTY) match {
        case null => None
        case uri  => Some(new java.net.URI(uri))
      }
    } catch {
      case e: IOException => None
    }
  }
  def writeProperties(f: File, uri: URI): Unit = {
    val props = new java.util.Properties
    props.setProperty(SERVER_URI_PROPERTY, uri.toASCIIString)
    val output = new java.io.FileOutputStream(f)
    val df = new java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mmZ")
    df.setTimeZone(java.util.TimeZone.getTimeZone("UTC"))
    Pre.writeProperties(props, f, s"Server Startup at ${df.format(new java.util.Date)}")
  }

  def isReachable(uri: java.net.URI): Boolean =
    try {
      // TODO - For now we assume if we can connect, it means
      // that the server is working...
      val socket = new java.net.Socket(uri.getHost, uri.getPort)
      try socket.isConnected
      finally socket.close()
    } catch {
      case e: IOException => false
    }
}
/** A helper class that dumps incoming values into a print stream. */
class StreamDumper(in: java.io.BufferedReader, out: java.io.PrintStream) extends Thread {
  // Don't block the application for this thread.
  setDaemon(true)
  val endTime = new java.util.concurrent.atomic.AtomicLong(Long.MaxValue)
  override def run(): Unit = {
    def read(): Unit = if (endTime.get > System.currentTimeMillis) in.readLine match {
      case null => ()
      case line =>
        out.println(line)
        out.flush()
        read()
    }
    read()
    out.close()
  }

  def close(waitForErrors: Boolean): Unit = {
    // closing "in" blocks forever on Windows, so don't do it;
    // just wait a couple seconds to read more stuff if there is
    // any stuff.
    if (waitForErrors) {
      endTime.set(System.currentTimeMillis + 5000)
      // at this point we'd rather the dumper thread run
      // before we check whether to sleep
      Thread.`yield`()
      // let ourselves read more (thread should exit on earlier of endTime or EOF)
      while (isAlive() && (endTime.get > System.currentTimeMillis))
        Thread.sleep(50)
    } else {
      endTime.set(System.currentTimeMillis)
    }
  }
}
object ServerLauncher {
  import ServerApplication.SERVER_SYNCH_TEXT
  def startServer(currentDirectory: File, config: LaunchConfiguration): URI = {
    val serverConfig = config.serverConfig match {
      case Some(c) => c
      case None    => throw new RuntimeException("Logic Failure:  Attempting to start a server that isn't configured to be a server.  Please report a bug.")
    }
    val launchConfig = java.io.File.createTempFile("sbtlaunch", "config")
    if (System.getenv("SBT_SERVER_SAVE_TEMPS") eq null)
      launchConfig.deleteOnExit()
    LaunchConfiguration.save(config, launchConfig)
    val jvmArgs: List[String] = serverJvmArgs(currentDirectory, serverConfig)
    val cmd: List[String] =
      ("java" :: jvmArgs) ++
        ("-jar" :: defaultLauncherLookup.getCanonicalPath :: s"@load:${launchConfig.toURI.toURL.toString}" :: Nil)
    launchProcessAndGetUri(cmd, currentDirectory)
  }

  // Here we try to isolate all the stupidity of dealing with Java processes.
  def launchProcessAndGetUri(cmd: List[String], cwd: File): URI = {
    // TODO - Handle windows path stupidity in arguments.
    val pb = new java.lang.ProcessBuilder()
    pb.command(cmd: _*)
    pb.directory(cwd)
    val process = pb.start()
    // First we need to grab all the input streams, and close the ones we don't care about.
    process.getOutputStream.close()
    val stderr = process.getErrorStream
    val stdout = process.getInputStream
    // Now we start dumping out errors.
    val errorDumper = new StreamDumper(new java.io.BufferedReader(new java.io.InputStreamReader(stderr)), System.err)
    errorDumper.start()
    // Now we look for the URI synch value, and then make sure we close the output files.
    try readUntilSynch(new java.io.BufferedReader(new java.io.InputStreamReader(stdout))) match {
      case Some(uri) => uri
      case _ =>
        // attempt to get rid of the server (helps prevent hanging / stuck locks,
        // though this is not reliable)
        try process.destroy() catch { case e: Exception => }
        // block a second to try to get stuff from stderr
        errorDumper.close(waitForErrors = true)
        sys.error(s"Failed to start server process in ${pb.directory} command line ${pb.command}")
    } finally {
      errorDumper.close(waitForErrors = false)
      stdout.close()
      // Do not close stderr here because on Windows that will block,
      // and since the child process has no reason to exit, it may
      // block forever. errorDumper.close() instead owns the problem
      // of deciding what to do with stderr.
    }
  }

  object ServerUriLine {
    def unapply(in: String): Option[URI] =
      if (in startsWith SERVER_SYNCH_TEXT) {
        Some(new URI(in.substring(SERVER_SYNCH_TEXT.size)))
      } else None
  }
  /** Reads an input steam until it hits the server synch text and server URI. */
  def readUntilSynch(in: java.io.BufferedReader): Option[URI] = {
    @tailrec
    def read(): Option[URI] = in.readLine match {
      case null               => None
      case ServerUriLine(uri) => Some(uri)
      case line               => read()
    }
    try read()
    finally in.close()
  }
  /** Reads all the lines in a file. If it doesn't exist, returns an empty list.  Forces UTF-8 strings. */
  def readLines(f: File): List[String] =
    if (!f.exists) Nil else {
      val reader = new java.io.BufferedReader(new java.io.InputStreamReader(new java.io.FileInputStream(f), "UTF-8"))
      @tailrec
      def read(current: List[String]): List[String] =
        reader.readLine match {
          case null => current.reverse
          case line => read(line :: current)
        }
      try read(Nil)
      finally reader.close()
    }

  // None = couldn't figure it out
  def javaIsAbove(currentDirectory: File, version: Int): Option[Boolean] = try {
    val pb = new java.lang.ProcessBuilder()
    // hopefully "java -version" is a lot faster than booting the full JVM.
    // not sure how else we can do this.
    pb.command("java", "-version")
    pb.directory(currentDirectory)
    val process = pb.start()
    try {
      process.getOutputStream.close()
      process.getInputStream.close()
      val stderr = new java.io.LineNumberReader(new java.io.InputStreamReader(process.getErrorStream))
      // Looking for the first line which is `java version "1.7.0_60"` or similar
      val lineOption = try Option(stderr.readLine()) finally stderr.close()
      val re = """java version "[0-9]+\.([0-9]+)\..*".*""".r
      lineOption flatMap {
        case re(v) => try Some(Integer.parseInt(v) > version) catch { case NonFatal(_) => None }
        case other =>
          System.err.println(s"Failed to parse version from 'java -version' output '$other'")
          None
      }
    } finally {
      process.destroy()
      try { process.waitFor() } catch { case NonFatal(_) => }
    }
  } catch {
    case e: IOException =>
      // both process.start and reading the output streams can throw IOException.
      // all OS exceptions from process.start are supposed to be IOException.
      System.err.println(s"Failed to run 'java -version': ${e.getClass.getName}: ${e.getMessage}")
      None
  }

  def serverJvmArgs(currentDirectory: File, serverConfig: ServerConfiguration): List[String] =
    serverJvmArgs(currentDirectory, serverConfig.jvmArgs map readLines getOrElse Nil)

  final val memOptPrefixes = List("-Xmx", "-Xms", "-XX:MaxPermSize", "-XX:PermSize", "-XX:ReservedCodeCacheSize", "-XX:MaxMetaspaceSize", "-XX:MetaspaceSize")

  final val defaultMinHeapM = 256
  final val defaultMaxHeapM = defaultMinHeapM * 4
  final val defaultMinPermM = 64
  final val defaultMaxPermM = defaultMinPermM * 4

  // this is separate just for the test suite
  def serverJvmArgs(currentDirectory: File, baseArgs: List[String]): List[String] = {
    // ignore blank lines
    val trimmed = baseArgs.map(_.trim).filterNot(_.isEmpty)
    // If the user config has provided ANY memory options we bail out and do NOT add
    // any defaults. This means people can always fix our mistakes, and it avoids
    // issues where the JVM refuses to start because of (for example) min size greater
    // than max size. We don't want to deal with coordinating our changes with the
    // user configuration.
    def isMemoryOption(s: String) = memOptPrefixes.exists(s.startsWith(_))
    if (trimmed.exists(isMemoryOption(_)))
      trimmed
    else {
      val permOptions = javaIsAbove(currentDirectory, 7) match {
        case Some(true)  => List(s"-XX:MetaspaceSize=${defaultMinPermM}m", s"-XX:MaxMetaspaceSize=${defaultMaxPermM}m")
        case Some(false) => List(s"-XX:PermSize=${defaultMinPermM}m", s"-XX:MaxPermSize=${defaultMaxPermM}m")
        case None        => Nil // don't know what we're doing, so don't set options
      }
      s"-Xms${defaultMinHeapM}m" :: s"-Xmx${defaultMaxHeapM}m" :: (permOptions ++ trimmed)
    }
  }

  def defaultLauncherLookup: File =
    try {
      val classInLauncher = classOf[AppConfiguration]
      val fileOpt = for {
        domain <- Option(classInLauncher.getProtectionDomain)
        source <- Option(domain.getCodeSource)
        location = source.getLocation
      } yield toFile(location)
      fileOpt.getOrElse(throw new RuntimeException("Could not inspect protection domain or code source"))
    } catch {
      case e: Throwable => throw new RuntimeException("Unable to find sbt-launch.jar.", e)
    }
}
