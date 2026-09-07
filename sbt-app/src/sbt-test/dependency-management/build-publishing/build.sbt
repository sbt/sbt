/*
 * sbt
 * Copyright 2026, Scala center
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import com.jsuereth.sbtpgp.PgpKeys.signedArtifacts
import com.jsuereth.pgp.PGP

ThisBuild / organization := "org.sbt.build-publishing"
ThisBuild / version := "1.0-SNAPSHOT"
ThisBuild / crossPaths := false
ThisBuild / autoScalaLibrary := false
val isolated = Seq(
  csrCacheDirectory := (ThisBuild / baseDirectory).value / "resolution-cache",
  ivyPaths := IvyPaths(
    (ThisBuild / baseDirectory).value.toString,
    Some(((ThisBuild / baseDirectory).value / "ivy-home").toString)
  ),
  resolvers += Resolver.file(
    "fixture-local",
    (ThisBuild / baseDirectory).value / "ivy-home" / "local"
  )(Resolver.ivyStylePatterns)
)

val prepare = taskKey[Unit]("Generate temporary signing keys and package fixtures")
val checkBinary = taskKey[Unit]("Check binary-only publication and dependency metadata")
val checkSigned = taskKey[Unit]("Check local and remote package signatures")
val checkUnsigned = taskKey[Unit]("Check signing skip retains only original artifacts")
val clearPackages = taskKey[Unit]("Clear previously signed publications")
val overwriteFixtures = taskKey[Unit]("Replace snapshot package content")

lazy val root = project
  .in(file("."))
  .enablePlugins(PackageSignerPlugin)
  .settings(isolated)
  .settings(
    name := "build-publishing",
    libraryDependencies += organization.value % "leaf" % version.value,
    Compile / doc := sys.error("publishLocalBin must not generate documentation"),
    publishTo := Some(
      Resolver.file("signed", baseDirectory.value / "remote")(Resolver.mavenStylePatterns)
    ),
    publish / checksums := Seq("sha1", "md5"),
    useGpg := false,
    pgpPassphrase := Some("fixture".toCharArray),
    pgpSigningKey := None,
    pgpPublicRing := baseDirectory.value / "keys" / "public.asc",
    pgpSecretRing := baseDirectory.value / "keys" / "secret.asc",

    prepare := Def.uncached {
      val root = baseDirectory.value
      assert(otherResolvers.value.count(_.name == "signed") == 1)
      IO.createDirectory(root / "keys")
      PGP.makeKeys(
        "sbt scripted fixture",
        "fixture".toCharArray,
        pgpPublicRing.value,
        pgpSecretRing.value
      )
      Seq("zip", "deb", "rpm").foreach(ext => IO.write(root / s"package.$ext", "first"))
    },

    Seq(Universal -> "zip", Debian -> "deb", Rpm -> "rpm").flatMap { (conf, ext) =>
      inConfig(conf)(
        Seq(
          packagedArtifacts := Def.uncached {
            Map(
              Artifact("build-publishing", ext, ext) -> fileConverter.value.toVirtualFile(
                (baseDirectory.value / s"package.$ext").toPath
              )
            )
          }
        )
      )
    },

    checkBinary := Def.uncached {
      val root = baseDirectory.value
      val dir = root / "ivy-home" / "local" / organization.value / name.value / version.value
      Seq(
        "jars/build-publishing.jar",
        "srcs/build-publishing-sources.jar",
        "poms/build-publishing.pom",
        "ivys/ivy.xml",
        "docs/build-publishing-javadoc.jar"
      ).foreach { path =>
        assert((dir / path).isFile, s"Missing $path")
      }
      val sources = new java.util.jar.JarFile(dir / "srcs/build-publishing-sources.jar")
      try assert(sources.getEntry("Library.java") != null)
      finally sources.close()
      assert((dir / "docs/build-publishing-javadoc.jar").length == 0)
      assert(IO.read(dir / "ivys/ivy.xml").contains("name=\"leaf\""))
      assert(IO.read(dir / "poms/build-publishing.pom").contains("<artifactId>leaf</artifactId>"))
      val jar = new java.util.jar.JarFile(dir / "jars/build-publishing.jar")
      try assert(jar.getEntry("Library.class") != null)
      finally jar.close()
    },

    checkSigned := Def.uncached {
      val root = baseDirectory.value
      val local = root / "ivy-home" / "local" / organization.value / name.value / version.value
      val remote =
        root / "remote" / organization.value.replace('.', '/') / name.value / version.value
      val publicKey = PGP.loadPublicKeyRing(pgpPublicRing.value)
      Seq("zip", "deb", "rpm").foreach { ext =>
        Seq(
          local / s"${ext}s/build-publishing.$ext",
          remote / s"build-publishing-${version.value}.$ext"
        ).foreach { f =>
          assert(IO.read(f) == IO.read(root / s"package.$ext"), s"Incorrect content in $f")
          val signature = file(f.toString + ".asc")
          assert(publicKey.verifySignatureFile(f, signature), s"Invalid signature for $f")
          Seq("sha1", "md5").foreach { hash =>
            val algorithm = if (hash == "sha1") "SHA-1" else "MD5"
            val digest = java.security.MessageDigest
              .getInstance(algorithm)
              .digest(IO.readBytes(f))
              .map(b => f"${b & 0xff}%02x")
              .mkString
            assert(IO.read(file(f.toString + s".$hash")).trim == digest)
            assert(!file(signature.toString + s".$hash").exists)
          }
        }
      }
      val tampered = root / "tampered.zip"
      IO.write(tampered, "tampered")
      assert(!publicKey.verifySignatureFile(tampered, local / "zips/build-publishing.zip.asc"))
      assert((local / "ivys/ivy.xml").isFile)
    },

    checkUnsigned := Def.uncached {
      val root = baseDirectory.value
      val local = root / "ivy-home" / "local" / organization.value / name.value / version.value
      val remote =
        root / "remote" / organization.value.replace('.', '/') / name.value / version.value
      Seq("zip", "deb", "rpm").foreach { ext =>
        Seq(
          local / s"${ext}s/build-publishing.$ext",
          remote / s"build-publishing-${version.value}.$ext"
        ).foreach { f =>
          assert(IO.read(f) == "second")
          assert(!file(f.toString + ".asc").exists)
        }
      }
      val converter = fileConverter.value
      Seq(
        (Universal / signedArtifacts).value,
        (Debian / signedArtifacts).value,
        (Rpm / signedArtifacts).value
      ).foreach { artifacts =>
        assert(artifacts.size == 1)
        artifacts.foreach { (artifact, ref) =>
          assert(!artifact.extension.endsWith(".asc"))
          assert(converter.toPath(ref).toFile.isFile)
        }
      }
    },

    clearPackages := Def.uncached {
      val local =
        baseDirectory.value / "ivy-home" / "local" / organization.value / name.value / version.value
      Seq("zips", "debs", "rpms").foreach(dir => IO.delete(local / dir))
      IO.delete(baseDirectory.value / "remote")
    },

    overwriteFixtures := Def.uncached {
      Seq("zip", "deb", "rpm")
        .foreach(ext => IO.write(baseDirectory.value / s"package.$ext", "second"))
    }
  )

lazy val leaf = project.settings(isolated).settings(name := "leaf")
lazy val consumer = project
  .settings(isolated)
  .settings(
    name := "consumer",
    libraryDependencies += organization.value % "build-publishing" % version.value
  )
