ThisBuild / csrCacheDirectory := (ThisBuild / baseDirectory).value / "coursier-cache"

name := "lib1"
organization := "com.example"
version := "0.1.0-SNAPSHOT"
scalaVersion := "3.8.1"

// Use ivyless publisher
useIvy := false

// Disable doc generation to speed up the test
Compile / packageDoc / publishArtifact := false
Compile / packageSrc / publishArtifact := false

// Directory where the HTTP server will store uploaded files
val repoDir = settingKey[File]("Repository directory for the test HTTP server")
repoDir := baseDirectory.value / "repo"

// Port for the test HTTP server
val serverPort = settingKey[Int]("Port for the test HTTP server")
serverPort := 18080

// Configure publishTo to use the local HTTP server with Ivy-style patterns
publishTo := Some(
  Resolver.url(
    "test-repo",
    new java.net.URL(s"http://localhost:${serverPort.value}/")
  )(Resolver.ivyStylePatterns)
)

// Task to start the HTTP server
val startServer = taskKey[Unit]("Start the test HTTP server")
startServer := {
  val log = streams.value.log
  val port = serverPort.value
  val repo = repoDir.value

  IO.delete(repo)
  IO.createDirectory(repo)

  log.info(s"Starting HTTP server on port $port, serving from $repo")

  // Start server in a background thread
  val server = com.sun.net.httpserver.HttpServer.create(
    new java.net.InetSocketAddress(port), 0
  )

  server.createContext("/", new com.sun.net.httpserver.HttpHandler {
    def handle(exchange: com.sun.net.httpserver.HttpExchange): Unit = {
      val method = exchange.getRequestMethod
      val path = exchange.getRequestURI.getPath
      val targetFile = new File(repo, path)

      log.debug(s"$method $path -> $targetFile")

      method match {
        case "PUT" =>
          // Create parent directories
          IO.createDirectory(targetFile.getParentFile)

          // Read request body and write to file
          val body = IO.readBytes(exchange.getRequestBody)
          IO.write(targetFile, body)

          exchange.sendResponseHeaders(201, -1)
          exchange.close()
          log.info(s"Uploaded: $path (${body.length} bytes)")

        case "GET" =>
          if (targetFile.exists) {
            val bytes = IO.readBytes(targetFile)
            exchange.sendResponseHeaders(200, bytes.length)
            exchange.getResponseBody.write(bytes)
            exchange.close()
          } else {
            exchange.sendResponseHeaders(404, -1)
            exchange.close()
          }

        case "HEAD" =>
          if (targetFile.exists) {
            exchange.sendResponseHeaders(200, -1)
          } else {
            exchange.sendResponseHeaders(404, -1)
          }
          exchange.close()

        case _ =>
          exchange.sendResponseHeaders(405, -1)
          exchange.close()
      }
    }
  })

  server.setExecutor(null)
  server.start()

  // Store server reference for cleanup
  val serverRef = new java.util.concurrent.atomic.AtomicReference(server)
  sys.props.put("test.http.server", serverRef.toString)

  log.info(s"HTTP server started on http://localhost:$port/")
}

// Task to stop the HTTP server
val stopServer = taskKey[Unit]("Stop the test HTTP server")
stopServer := {
  val log = streams.value.log
  log.info("Note: Server will be stopped when sbt exits")
}

// Task to print debug info
val printPaths = taskKey[Unit]("Print paths for debugging")
printPaths := {
  val log = streams.value.log
  log.info(s"repoDir = ${repoDir.value}")
  log.info(s"serverPort = ${serverPort.value}")
  log.info(s"publishTo = ${publishTo.value}")
  log.info(s"useIvy = ${useIvy.value}")
}

// Task to check that files were published correctly
val checkPublish = taskKey[Unit]("Check that publish produced the expected files")
checkPublish := {
  val log = streams.value.log
  val repo = repoDir.value
  val org = organization.value.replace('.', '/')
  val moduleName = normalizedName.value + "_3"
  val ver = version.value

  // Expected path based on Ivy patterns:
  // [organisation]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]
  val moduleDir = repo / org / moduleName / ver

  log.info(s"Checking published files in $moduleDir")

  // List what's actually in the repo directory
  def listDir(dir: File, indent: String = ""): Unit = {
    if (dir.exists) {
      dir.listFiles.toSeq.sorted.foreach { f =>
        log.info(s"$indent${f.getName}")
        if (f.isDirectory) listDir(f, indent + "  ")
      }
    } else {
      log.info(s"${indent}Directory does not exist: $dir")
    }
  }
  log.info("Contents of repo:")
  listDir(repo)

  // Check that the main artifacts exist
  val expectedDirs = Seq("jars", "poms", "ivys")
  expectedDirs.foreach { dir =>
    val d = moduleDir / dir
    assert(d.exists && d.isDirectory, s"Expected directory $d to exist")
  }

  // Check jar file and checksums
  val jarFile = moduleDir / "jars" / s"$moduleName.jar"
  assert(jarFile.exists, s"Expected $jarFile to exist")
  assert((moduleDir / "jars" / s"$moduleName.jar.md5").exists, s"Expected md5 checksum to exist")
  assert((moduleDir / "jars" / s"$moduleName.jar.sha1").exists, s"Expected sha1 checksum to exist")

  // Check ivy.xml and checksums
  val ivyFile = moduleDir / "ivys" / "ivy.xml"
  assert(ivyFile.exists, s"Expected $ivyFile to exist")
  assert((moduleDir / "ivys" / "ivy.xml.md5").exists, s"Expected ivy.xml md5 checksum to exist")
  assert((moduleDir / "ivys" / "ivy.xml.sha1").exists, s"Expected ivy.xml sha1 checksum to exist")

  // Check ivy.xml content
  val ivyContent = IO.read(ivyFile)
  assert(ivyContent.contains(s"""organisation="${organization.value}""""), s"ivy.xml should contain organisation")
  assert(ivyContent.contains(s"""module="$moduleName""""), s"ivy.xml should contain module name")
  assert(ivyContent.contains(s"""revision="$ver""""), s"ivy.xml should contain revision")

  log.success("All publish checks passed!")
}

// Task to clean the repo
val cleanRepo = taskKey[Unit]("Clean the repo directory")
cleanRepo := {
  IO.delete(repoDir.value)
}
