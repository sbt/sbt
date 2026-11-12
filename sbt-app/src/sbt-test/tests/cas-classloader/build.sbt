import java.nio.file.{ Files, FileSystemException, StandardCopyOption }
import sbt.util.{ Digest, DiskActionCacheStore }

ThisBuild / scalaVersion := "2.13.18"
Global / localCacheDirectory := baseDirectory.value / "diskcache"

lazy val linkCoreJar = taskKey[Unit]("Link the core archive to its CAS blob")
lazy val core = project.settings(exportJars := true)

lazy val app = project
  .dependsOn(core)
  .settings(
    Test / fork := false,
    libraryDependencies ++= Seq(
      "org.scala-lang" % "scala-compiler" % scalaVersion.value % Test,
      "org.scalameta" %% "munit" % "1.3.6" % Test
    ),
    linkCoreJar := Def.uncached {
      val converter = fileConverter.value
      val cache = DiskActionCacheStore((Global / localCacheDirectory).value.toPath, converter)
      val ref = (core / Compile / packageInternal).value
      val jar = converter.toPath(ref)
      val log = streams.value.log
      if !Files.isSymbolicLink(jar) then
        val blob = cache.putBlob(jar, Digest(ref)).toAbsolutePath
        val link = jar.resolveSibling(s"${jar.getFileName}.link")
        try Files.createSymbolicLink(link, blob)
        catch
          case e: FileSystemException if scala.util.Properties.isWin =>
            log.info(s"Could not create a symbolic link on Windows: ${e.getMessage}")
        if Files.isSymbolicLink(link) then Files.move(link, jar, StandardCopyOption.REPLACE_EXISTING)
      ()
    }
  )
