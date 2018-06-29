val logFile = settingKey[File]("")

// Arbitrary dependency with no transitive dependencies
libraryDependencies += "org.slf4j" % "slf4j-api" % "1.7.25"
// We want to control when the cache gets a hit
coursierCache := baseDirectory.value / "cache"
logFile := baseDirectory.value / "log"

coursierLoggerFactory := {
  val logStream = new java.io.PrintStream(logFile.value)
  def log(msg: String): Unit = {
    println(msg)
    logStream.println(msg)
  }
  val cacheFile = coursierCache.value
  ;{ () =>
    new coursier.Cache.Logger {
      override def init(beforeOutput: => Unit): Unit = {
        beforeOutput
        log("init")
      }
      override def foundLocally(url: String, file: File): Unit = {
        log(s"found $url at ${IO.relativize(cacheFile, file).getOrElse(file)}")
      }
      override def downloadingArtifact(url: String, file: File): Unit = {
        log(s"downloading $url to ${IO.relativize(cacheFile, file).getOrElse(file)}")
      }
      override def downloadedArtifact(url: String, success: Boolean): Unit = {
        log(s"downloaded $url: $success")
      }
      override def stopDidPrintSomething(): Boolean = {
        log("stop")
        true
      }
    }
  }
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
  val downloadingMsgStart = s"downloading $url to "
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
  val msg = s"found $url at "
  if (!log.exists(_.startsWith(msg)))
    sys.error(s"log doesn't contain line starting with '$msg'")
}
