import sbt.internal.util.CacheEventSummary
import java.nio.charset.StandardCharsets.ISO_8859_1
import java.nio.file.Files
import java.util.zip.GZIPInputStream
import scala.util.Using

lazy val checkHit = taskKey[Unit]("asserts the previous command was a pure cache hit")
lazy val checkPortableAnalysis = taskKey[Unit]("asserts the analysis holds no machine path")

Global / localCacheDirectory := baseDirectory.value / "diskcache"

ThisBuild / scalaVersion := "3.8.4"
ThisBuild / usePipelining := true
ThisBuild / semanticdbEnabled := true

lazy val plugin = project.settings(
  libraryDependencies += "org.scala-lang" %% "scala3-compiler" % scalaVersion.value,
)

lazy val use = project
  .dependsOn(plugin % Configurations.CompilerPlugin)
  .settings(
    autoCompilerPlugins := true,
    checkPortableAnalysis := Def.uncached {
      val analysis = (Compile / compileAnalysisFile).value.toPath
      val stored = Using.resource(new GZIPInputStream(Files.newInputStream(analysis))): in =>
        new String(in.readAllBytes(), ISO_8859_1)
      Seq((ThisBuild / baseDirectory).value.toPath.toString, sys.props("user.home")).foreach: p =>
        assert(!stored.contains(p), s"$analysis holds the machine path $p")
    },
  )

lazy val app = project.dependsOn(use)

checkHit := Def.uncached {
  val config = Def.cacheConfiguration.value
  val prev = config.cacheEventLog.previous match
    case s: CacheEventSummary.Data => s
    case _                         => sys.error("empty event log")
  streams.value.log.info(s"hitCount=${prev.hitCount} missCount=${prev.missCount}")
  assert(prev.missCount == 0, s"expected a pure cache hit but missCount=${prev.missCount}")
}
