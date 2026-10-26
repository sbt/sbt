lazy val verify = "com.eed3si9n.verify" %% "verify" % "1.0.0"

Global / localCacheDirectory := baseDirectory.value / "diskcache"

scalaVersion := "3.9.0"
libraryDependencies += verify % Test
testFrameworks += new TestFramework("verify.runner.Framework")
Test / test / cacheStores := Nil

val acEntries = settingKey[File]("")
val snapshotAc = taskKey[Unit]("")
val checkNoNewAc = taskKey[Unit]("")

acEntries := baseDirectory.value / "ac-before.txt"

snapshotAc := Def.uncached {
  val ac = (Global / localCacheDirectory).value / "ac"
  IO.writeLines(acEntries.value, IO.listFiles(ac).map(_.getName).toList)
}

checkNoNewAc := Def.uncached {
  val ac = (Global / localCacheDirectory).value / "ac"
  val before = IO.readLines(acEntries.value).toSet
  val added = IO.listFiles(ac).map(_.getName).filterNot(before)
  assert(added.isEmpty, s"new action cache entries: ${added.mkString(", ")}")
}
