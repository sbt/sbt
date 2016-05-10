scalaVersion := "2.11.8"

coursierCachePolicies := {
  if (sys.props("os.name").startsWith("Windows"))
    coursierCachePolicies.value
  else
    Seq(coursier.CachePolicy.ForceDownload)
}

libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.41" from {
  
  val f = file(sys.props("sbttest.base")) / "sbt-coursier" / "from" / "shapeless_2.11-2.3.0.jar"

  if (!f.exists()) {
    val url0 = "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.11/2.3.0/shapeless_2.11-2.3.0.jar"

    scala.Console.err.println(s"Fetching $url0")

    val url = new java.net.URL(url0)

    val is = url.openStream()
    val os = new java.io.FileOutputStream(f)

    var read = -1
    val b = Array.fill[Byte](16*1024)(0)
    while ({
      read = is.read(b)
      read >= 0
    }) {
      os.write(b, 0, read)
    }

    is.close()
    os.close()
  }

  f.toURI.toString
}