import sbt.internal.util.StringVirtualFile1
import sjsonnew.BasicJsonProtocol.*
import sbt.nio.file.{ Glob, RecursiveGlob }

val pure1 = taskKey[Unit]("")
val checkCacheNonEmpty = taskKey[Unit]("")
val checkCacheCleared = taskKey[Unit]("")
val checkNoStoreLinks = taskKey[Unit]("")

// The store directory name is unique to this test on purpose: scripted batch mode
// shares one sandbox directory and one sbt JVM across the cache/* tests and silently
// skips files it fails to delete between tests, so a Windows-locked blob left behind
// by a preceding test using the shared "diskcache" name would otherwise show up in
// this store and break checkCacheCleared.
Global / localCacheDirectory := baseDirectory.value / "clear-caches-diskcache"
Global / cleanKeepGlobs += Glob(baseDirectory.value / "clear-caches-diskcache" / "keep", RecursiveGlob)

def storeEntries(base: File): Set[String] =
  for {
    dir <- Set("cas", "ac")
    file <- Option((base / dir).listFiles).fold(Set.empty[String])(_.map(_.getName).toSet)
  } yield s"$dir/$file"

pure1 := {
  val output = StringVirtualFile1("${OUT}/a.txt", "foo")
  Def.declareOutput(output)
  ()
}

checkCacheNonEmpty := Def.uncached {
  assert(storeEntries(baseDirectory.value / "clear-caches-diskcache").nonEmpty, "no action cache entries present")
}

checkCacheCleared := Def.uncached {
  val leftover = storeEntries(baseDirectory.value / "clear-caches-diskcache")
  assert(leftover.isEmpty, s"entries survived clearCaches: ${leftover.mkString(", ")}")
}

checkNoStoreLinks := Def.uncached {
  import java.nio.file.Files
  val out = (baseDirectory.value / "target" / "out").toPath
  val diskcache = (baseDirectory.value / "clear-caches-diskcache").toPath.toAbsolutePath.normalize
  if (Files.exists(out)) {
    val stream = Files.walk(out)
    try {
      val it = stream.iterator()
      while (it.hasNext) {
        val p = it.next()
        if (Files.isSymbolicLink(p)) {
          val raw = Files.readSymbolicLink(p)
          val resolved =
            (if (raw.isAbsolute) raw else Option(p.getParent).fold(raw)(_.resolve(raw)))
              .normalize()
          assert(!resolved.startsWith(diskcache), s"$p still points into $diskcache")
        }
      }
    } finally stream.close()
  }
}
