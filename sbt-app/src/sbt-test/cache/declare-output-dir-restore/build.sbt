import sbt.internal.util.CacheEventSummary
import xsbti.HashedVirtualFileRef

val declareDir = taskKey[HashedVirtualFileRef]("writes 2 files into a dir and declares the dir")
val checkFiles = taskKey[Unit]("asserts both files exist")
val checkGone = taskKey[Unit]("asserts the dir has no files")
val delDir = taskKey[Unit]("deletes the generated dir")
val delZip = taskKey[Unit]("deletes the sibling .sbtdir.zip")
val checkHit = taskKey[Unit]("asserts previous command was a pure cache hit")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val declareOutputDirRestore = project.in(file("."))

declareDir := {
  val log = streams.value.log
  val dir = target.value / "gen-dir"
  IO.createDirectory(dir)
  IO.write(dir / "a.txt", "contents A")
  IO.write(dir / "b.txt", "contents B")
  log.info(s"COMPUTED declareDir (cache miss)")
  val vf = fileConverter.value.toVirtualFile(dir.toPath)
  Def.declareOutputDirectory(vf)
}

checkFiles := Def.uncached {
  val log = streams.value.log
  val dir = target.value / "gen-dir"
  val listing = if (dir.exists) (dir ** "*").get().mkString(", ") else "<dir missing>"
  log.info(s"gen-dir listing: $listing")
  assert((dir / "a.txt").exists, s"a.txt missing under $dir")
  assert((dir / "b.txt").exists, s"b.txt missing under $dir")
}

checkGone := Def.uncached {
  val dir = target.value / "gen-dir"
  assert(!(dir / "a.txt").exists && !(dir / "b.txt").exists, s"files still present under $dir")
}

delDir := Def.uncached {
  val dir = target.value / "gen-dir"
  IO.delete(dir)
  streams.value.log.info(s"deleted $dir")
}

delZip := Def.uncached {
  val zip = new java.io.File(target.value, "gen-dir.sbtdir.zip")
  streams.value.log.info(s"deleting $zip (exists=${zip.exists})")
  IO.delete(zip)
}

checkHit := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error("empty event log")
  streams.value.log.info(s"prev hitCount=${prev.hitCount} missCount=${prev.missCount}")
  assert(prev.missCount == 0, s"expected pure hit but missCount=${prev.missCount}")
  assert(prev.hitCount >= 1, s"expected a hit but hitCount=${prev.hitCount}")
}
