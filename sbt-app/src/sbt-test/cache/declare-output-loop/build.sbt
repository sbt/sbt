import sbt.internal.util.CacheEventSummary
import xsbti.HashedVirtualFileRef

val declareLoop = taskKey[Seq[HashedVirtualFileRef]]("declares 3 files via .map over a runtime list")
val checkAll = taskKey[Unit]("asserts all 3 files exist")
val delFiles = taskKey[Unit]("deletes the 3 files")
val checkNone = taskKey[Unit]("asserts none of the 3 files exist")
val checkHit = taskKey[Unit]("asserts previous command was a pure cache hit")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val declareOutputLoop = project.in(file("."))

declareLoop := {
  val log = streams.value.log
  val dir = target.value / "gen-multi"
  IO.createDirectory(dir)
  val files = List(dir / "a.txt", dir / "b.txt", dir / "c.txt")
  IO.write(files(0), "AAA")
  IO.write(files(1), "BBB")
  IO.write(files(2), "CCC")
  log.info(s"COMPUTED declareLoop (cache miss)")
  if (sys.props.contains("never.set.property")) {
    val ghost = fileConverter.value.toVirtualFile((dir / "never.txt").toPath)
    val _ = Def.declareOutput(ghost)
  }
  files.map { f =>
    val vf = fileConverter.value.toVirtualFile(f.toPath)
    Def.declareOutput(vf)
  }
}

def listing(dir: File): String =
  if (dir.exists) (dir ** "*").get().mkString(", ") else "<dir missing>"

checkAll := Def.uncached {
  val dir = target.value / "gen-multi"
  streams.value.log.info(s"gen-multi listing: ${listing(dir)}")
  assert((dir / "a.txt").exists, s"a.txt missing under $dir")
  assert((dir / "b.txt").exists, s"b.txt missing under $dir")
  assert((dir / "c.txt").exists, s"c.txt missing under $dir")
}

delFiles := Def.uncached {
  val dir = target.value / "gen-multi"
  IO.delete(Seq(dir / "a.txt", dir / "b.txt", dir / "c.txt"))
  streams.value.log.info(s"deleted files under $dir")
}

checkNone := Def.uncached {
  val dir = target.value / "gen-multi"
  assert(
    !(dir / "a.txt").exists && !(dir / "b.txt").exists && !(dir / "c.txt").exists,
    s"files still present under $dir"
  )
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
