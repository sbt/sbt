import java.nio.file.{ Files, LinkOption }
import java.nio.file.attribute.BasicFileAttributes

val recordIds = taskKey[Unit]("records the on-disk identity of the compile outputs")
val checkAnalysisChanged = taskKey[Unit]("asserts the analysis file was rewritten")
val checkAnalysisUnchanged = taskKey[Unit]("asserts the analysis file was left alone")
val checkClassesZipUnchanged = taskKey[Unit]("asserts the class directory was not re-packaged")
val checkNotModified = taskKey[Unit]("asserts zinc recompiled nothing")
val delClassesZip = taskKey[Unit]("deletes the sibling classes.sbtdir.zip")
val checkClasses = taskKey[Unit]("asserts class files are present")

// A rewritten output gets a fresh inode when the action cache relinks it into the CAS, and a fresh
// mtime where symlinks are unavailable; either one moving means the file was written again.
def idOf(f: File): String = {
  val attrs =
    Files.readAttributes(f.toPath, classOf[BasicFileAttributes], LinkOption.NOFOLLOW_LINKS)
  s"${Option(attrs.fileKey()).getOrElse("<no-file-key>")}|${attrs.lastModifiedTime().toMillis}"
}

lazy val classesZip = Def.task {
  val dir = (Compile / classDirectory).value
  new File(dir.getParentFile, dir.getName + ".sbtdir.zip")
}

lazy val idFile = Def.task { target.value / "recorded-ids.txt" }

def recorded(kind: String) = Def.task {
  IO.readLines(idFile.value)
    .collectFirst { case s"$k=$v" if k == kind => v }
    .getOrElse(sys.error(s"no recorded id for $kind"))
}

Global / localCacheDirectory := (ThisBuild / baseDirectory).value / "diskcache"
ThisBuild / scalaVersion := "3.9.0"
ThisBuild / exportJars := true

lazy val a = project.in(file("a"))

lazy val b = project
  .in(file("b"))
  .dependsOn(a)
  .settings(
    recordIds := Def.uncached {
      IO.writeLines(
        idFile.value,
        Seq(
          s"analysis=${idOf((Compile / compileAnalysisFile).value)}",
          s"classesZip=${idOf(classesZip.value)}",
        )
      )
    },
    checkAnalysisChanged := Def.uncached {
      val now = idOf((Compile / compileAnalysisFile).value)
      val before = recorded("analysis").value
      assert(now != before, s"analysis was not rewritten, so compile was a cache hit: $now")
    },
    checkAnalysisUnchanged := Def.uncached {
      val now = idOf((Compile / compileAnalysisFile).value)
      val before = recorded("analysis").value
      assert(now == before, s"analysis was rewritten: $before -> $now")
    },
    checkClassesZipUnchanged := Def.uncached {
      val now = idOf(classesZip.value)
      val before = recorded("classesZip").value
      assert(now == before, s"class directory was re-packaged: $before -> $now")
    },
    checkNotModified := Def.uncached {
      val (hasModified, _, _) = (Compile / compileIncremental).value
      assert(!hasModified, "zinc reported modified output, expected nothing to recompile")
    },
    delClassesZip := Def.uncached {
      IO.delete(classesZip.value)
    },
    checkClasses := Def.uncached {
      val classes = ((Compile / classDirectory).value ** "*.class").get()
      assert(classes.nonEmpty, "no class files")
    },
  )
