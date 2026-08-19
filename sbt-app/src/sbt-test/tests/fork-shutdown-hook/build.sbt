Global / localCacheDirectory := baseDirectory.value / "diskcache"
scalaVersion := "3.8.4"

Test / fork := true

libraryDependencies += "org.scalameta" %% "munit" % "1.0.4" % Test

val check = TaskKey[Unit]("check", "Verify the shutdown hook could load classes.")

check := Def.uncached {
  val file = baseDirectory.value / "hook-result.txt"
  val deadline = System.currentTimeMillis + 30000
  def content: String = if (file.exists) IO.read(file).trim else ""
  while (content.isEmpty || content.startsWith("PENDING")) {
    if (System.currentTimeMillis > deadline)
      sys.error(s"shutdown hook never completed: '$content'")
    Thread.sleep(500)
  }
  val result = content
  if (!result.startsWith("OK")) sys.error(s"shutdown hook failed: '$result'")
}
