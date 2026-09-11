import sbt.internal.util.CacheEventSummary

val delMixin = taskKey[Unit]("deletes the class files of the mixin object")
val corruptTrait = taskKey[Unit]("overwrites the trait's class file with junk")
val recordHashes = taskKey[Unit]("records the hash of every class file")
val checkHashes = taskKey[Unit]("asserts every class file matches the recorded hash")
val showClasses = taskKey[Unit]("lists the class files")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val classdirPartialRestore = project
  .in(file("."))
  .settings(
    scalaVersion := "3.8.4",
    Compile / mainClass := Some("example.Main"),
    delMixin := Def.uncached {
      val dir = (Compile / classDirectory).value
      val files = (dir ** "B*.class").get()
      assert(files.nonEmpty, s"no B*.class under $dir")
      IO.delete(files)
      streams.value.log.info(s"deleted ${files.mkString(", ")}")
    },
    corruptTrait := Def.uncached {
      val dir = (Compile / classDirectory).value
      val file = dir / "example" / "T.class"
      assert(file.exists, s"$file is missing")
      IO.delete(file)
      IO.write(file, "not a class file")
      streams.value.log.info(s"corrupted $file")
    },
    recordHashes := Def.uncached {
      val dir = (Compile / classDirectory).value
      val lines = (dir ** "*.class").get().sorted.map { f =>
        s"${dir.toPath.relativize(f.toPath)}=${Hash.toHex(Hash(f))}"
      }
      IO.writeLines(target.value / "recorded-hashes.txt", lines)
    },
    checkHashes := Def.uncached {
      val dir = (Compile / classDirectory).value
      val recorded = IO.readLines(target.value / "recorded-hashes.txt")
      val current = (dir ** "*.class").get().sorted.map { f =>
        s"${dir.toPath.relativize(f.toPath)}=${Hash.toHex(Hash(f))}"
      }
      assert(
        current == recorded,
        s"class directory does not match the cached output\nrecorded:\n  ${recorded.mkString("\n  ")}\ncurrent:\n  ${current.mkString("\n  ")}"
      )
    },
    showClasses := Def.uncached {
      val dir = (Compile / classDirectory).value
      streams.value.log.info(s"classes under $dir: ${(dir ** "*.class").get().mkString(", ")}")
    },
  )
