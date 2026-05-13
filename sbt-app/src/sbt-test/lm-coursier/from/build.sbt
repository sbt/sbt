scalaVersion := "2.12.21"

libraryDependencies += "com.chuusai" %% "shapeless" % "2.3.41" from {
  
  val f = file("shapeless_2.12-2.3.3.jar")

  if (!f.exists()) {
    val url0 = "https://repo1.maven.org/maven2/com/chuusai/shapeless_2.12/2.3.3/shapeless_2.12-2.3.3.jar"

    sLog.value.warn(s"Fetching $url0")

    val url = new java.net.URL(url0)

    val is = url.openStream()
    val os = new java.io.FileOutputStream(f)

    is.transferTo(os)

    is.close()
    os.close()
  }

  f.toURI.toString
}
