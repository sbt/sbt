// Test for offline mode with URL dependencies
libraryDependencies += ("org.jsoup" % "jsoup" % "1.9.1").from("https://jsoup.org/packages/jsoup-1.9.1.jar")

val checkOffline = taskKey[Unit]("Check that offline mode prevents URL access")
checkOffline := {
  // This should fail in offline mode if the dependency isn't cached
  val report = update.value
  val jars = report.allFiles
  println(s"Resolved ${jars.size} JARs")
  if (offline.value) {
    // In offline mode, should not have downloaded from URL
    val hasJsoup = jars.exists(_.getName.contains("jsoup"))
    if (!hasJsoup) {
      println("SUCCESS: Offline mode prevented URL download")
    } else {
      sys.error("FAILURE: Offline mode still downloaded from URL")
    }
  } else {
    println("Online mode - URL downloads allowed")
  }
}