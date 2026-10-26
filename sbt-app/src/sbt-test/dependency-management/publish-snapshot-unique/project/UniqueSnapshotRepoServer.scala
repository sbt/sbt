import java.io._
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import scala.collection.mutable
import com.sun.net.httpserver.{ HttpExchange, HttpHandler, HttpServer }

/**
 * Minimal stand-in for a Maven repository with unique snapshot naming enabled
 * (Artifactory "Maven Snapshot Version Behavior: Unique").
 *
 * A file uploaded under a `-SNAPSHOT` directory without a timestamp in its name
 * is stored under a server-assigned `<timestamp>-<buildNumber>` qualifier, and a
 * new qualifier is assigned per upload. Uploads that already carry a qualifier are
 * stored as sent. maven-metadata.xml is regenerated from what was stored.
 */
object UniqueSnapshotRepoServer {
  private var server: HttpServer = null

  private val checksumExts = Seq(".md5", ".sha1", ".sha256", ".sha512", ".asc")
  private val Snapshot = """^(.+)-([^/]+)-SNAPSHOT(?:-([^.]+))?\.(.+)$""".r
  private val Qualified = """-\d{8}\.\d{6}-\d+""".r

  private val counter = new AtomicInteger(0)
  private val qualifiers = new mutable.HashMap[String, (String, Int)]
  private val deployed = new mutable.HashMap[String, mutable.Map[(String, String), String]]

  private def nextQualifier(): (String, Int) = {
    val n = counter.incrementAndGet()
    (f"20260101.0000$n%02d", n)
  }

  private def splitChecksum(name: String): (String, String) =
    checksumExts.find(name.endsWith).map(ext => (name.dropRight(ext.length), ext)).getOrElse((name, ""))

  private def writeMetadata(dir: File, groupId: String, artifactId: String, baseVersion: String): Unit = {
    val (ts, bn) = qualifiers(dir.getPath)
    val entries = deployed.getOrElse(dir.getPath, mutable.Map.empty).toSeq.sortBy(_._1)
    val snapshotVersions = entries.map { case ((classifier, ext), value) =>
      val c = if (classifier.isEmpty) "" else s"<classifier>$classifier</classifier>"
      s"      <snapshotVersion>$c<extension>$ext</extension><value>$value</value></snapshotVersion>"
    }.mkString("\n")
    val xml =
      s"""<?xml version="1.0" encoding="UTF-8"?>
         |<metadata modelVersion="1.1.0">
         |  <groupId>$groupId</groupId>
         |  <artifactId>$artifactId</artifactId>
         |  <version>$baseVersion-SNAPSHOT</version>
         |  <versioning>
         |    <snapshot>
         |      <timestamp>$ts</timestamp>
         |      <buildNumber>$bn</buildNumber>
         |    </snapshot>
         |    <snapshotVersions>
         |$snapshotVersions
         |    </snapshotVersions>
         |  </versioning>
         |</metadata>
         |""".stripMargin
    val out = new PrintWriter(new File(dir, "maven-metadata.xml"), "UTF-8")
    try out.write(xml)
    finally out.close()
  }

  private def store(baseDir: File, relativePath: String, body: InputStream): Unit = {
    val segments = relativePath.split("/").toVector
    val fileName = segments.last
    val dir = new File(baseDir, segments.init.mkString(File.separator))
    dir.mkdirs()
    val (baseName, checksumExt) = splitChecksum(fileName)

    val targetName = baseName match {
      case _ if baseName == "maven-metadata.xml"        => fileName
      case _ if Qualified.findFirstIn(baseName).nonEmpty => fileName
      case Snapshot(artifact, version, classifier, ext) if dir.getName.endsWith("-SNAPSHOT") =>
        synchronized {
          if (checksumExt.isEmpty || !qualifiers.contains(dir.getPath))
            qualifiers.put(dir.getPath, nextQualifier())
          val (ts, bn) = qualifiers(dir.getPath)
          val suffix = Option(classifier).map("-" + _).getOrElse("")
          if (checksumExt.isEmpty) {
            deployed
              .getOrElseUpdate(dir.getPath, mutable.Map.empty)
              .put((Option(classifier).getOrElse(""), ext), s"$version-$ts-$bn")
            writeMetadata(dir, segments.init.init.init.mkString("."), segments.init.init.last, version)
          }
          s"$artifact-$version-$ts-$bn$suffix.$ext$checksumExt"
        }
      case _ => fileName
    }

    val out = new FileOutputStream(new File(dir, targetName))
    try body.transferTo(out)
    finally out.close()
  }

  def start(port: Int, baseDir: File): Unit = {
    if (server != null) stop()
    counter.set(0)
    qualifiers.clear()
    deployed.clear()
    server = HttpServer.create(new InetSocketAddress(port), 0)
    server.createContext("/", new HttpHandler {
      override def handle(ex: HttpExchange): Unit = {
        val path = ex.getRequestURI.getRawPath.stripPrefix("/")
        ex.getRequestMethod match {
          case "PUT" =>
            val in = ex.getRequestBody
            try store(baseDir, path, in)
            finally in.close()
            ex.sendResponseHeaders(201, -1)
          case "GET" | "HEAD" =>
            val f = new File(baseDir, path.replace("/", File.separator))
            if (f.isFile) {
              val bytes = java.nio.file.Files.readAllBytes(f.toPath)
              ex.sendResponseHeaders(200, bytes.length.toLong)
              val out = ex.getResponseBody
              try out.write(bytes)
              finally out.close()
            } else ex.sendResponseHeaders(404, -1)
          case _ => ex.sendResponseHeaders(405, -1)
        }
        ex.close()
      }
    })
    server.setExecutor(null)
    server.start()
  }

  def stop(): Unit =
    if (server != null) {
      server.stop(0)
      server = null
    }
}
