import java.util.zip.ZipFile
import scala.jdk.CollectionConverters.*

ThisBuild / scalaVersion := "3.9.0"
ThisBuild / usePipelining := true

Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val core = project

lazy val app = project
  .dependsOn(core)
  .settings(
    // The early jar core exports must hold every TASTy of core, not just the last round's.
    TaskKey[Unit]("checkEarlyJar") := Def.uncached {
      val c = fileConverter.value
      val jar = c.toPath((core / Compile / earlyOutput).value).toFile
      assert(jar.exists, s"early jar $jar is missing")
      val entries = new ZipFile(jar).entries.asScala.map(_.getName).filter(_.endsWith(".tasty")).toSet
      val expected = Set("core/Base.tasty", "core/Other.tasty", "core/Added.tasty")
      assert(entries == expected, s"early jar entries = $entries, expected $expected")
    },
  )
