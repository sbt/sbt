val logFile = settingKey[File]("")

// Arbitrary dependency with no transitive dependencies
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
// We want to control when the cache gets a hit
coursierCache := baseDirectory.value / "cache"
logFile := baseDirectory.value / "log"

coursierLogger := {
  var logStream: java.io.PrintStream = null
  def log(msg: String): Unit = {
    println(msg)
    logStream.println(msg)
  }
  val cacheFile = coursierCache.value

  val logger = new lmcoursier.definitions.CacheLogger {
    override def init(sizeHint: Option[Int]): Unit = {
      logStream = new java.io.PrintStream(
        new java.io.FileOutputStream(logFile.value, true)
      )
      log("init")
    }
    override def foundLocally(url: String): Unit = {
      log(s"found $url")
    }
    override def downloadingArtifact(url: String): Unit = {
      log(s"downloading $url")
    }
    override def downloadedArtifact(url: String, success: Boolean): Unit = {
      log(s"downloaded $url: $success")
    }
    override def stop(): Unit = {
      log("stop")
      logStream.flush()
      logStream.close()
      logStream = null
    }
  }

  Some(logger)
}

TaskKey[Unit]("checkDownloaded") := {
  val log = IO.readLines(logFile.value)
  if (log.head != "init") {
    sys.error(s"log started with '${log.head}', not init")
  }
  if (log.last != "stop") {
    sys.error(s"log ended with '${log.last}', not stop")
  }
  val url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar"
  val downloadedMsg = s"downloaded $url: true"
  val downloadingMsgStart = s"downloading $url"
  if (!log.contains(downloadedMsg))
    sys.error(s"log doesn't contain '$downloadedMsg'")
  if (!log.exists(_.startsWith(downloadingMsgStart)))
    sys.error(s"log doesn't contain line starting with '$downloadingMsgStart'")
}

TaskKey[Unit]("checkFound") := {
  val log = IO.readLines(logFile.value)
  if (log.head != "init") {
    sys.error(s"log started with '${log.head}', not init")
  }
  if (log.last != "stop") {
    sys.error(s"log ended with '${log.last}', not stop")
  }
  val url = "https://repo1.maven.org/maven2/org/slf4j/slf4j-api/1.7.25/slf4j-api-1.7.25.jar"
  val msg = s"found $url"
  if (!log.exists(_.startsWith(msg)))
    sys.error(s"log doesn't contain line starting with '$msg'")
}
