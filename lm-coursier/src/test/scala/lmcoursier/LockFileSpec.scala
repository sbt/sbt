package lmcoursier

import lmcoursier.internal.*
import org.scalatest.funsuite.AnyFunSuite
import java.io.File
import sbt.io.IO

class LockFileSpec extends AnyFunSuite {

  test("LockFileData serialization round-trip") {
    val lockData = LockFileData(
      version = "1.0",
      buildClock = "abc123",
      configurations = Vector(
        ConfigurationLock(
          name = "compile",
          dependencies = Vector(
            DependencyLock(
              organization = "org.scala-lang",
              name = "scala-library",
              version = "2.13.16",
              configuration = "compile",
              classifier = None,
              tpe = "jar",
              transitives = Vector("org.scala-lang:scala-library:2.13.16"),
              artifacts = Vector(
                ArtifactLock(
                  url =
                    "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.16/scala-library-2.13.16.jar",
                  classifier = None,
                  extension = "jar",
                  tpe = "jar"
                )
              )
            )
          )
        )
      ),
      metadata = LockFileMetadata(
        sbtVersion = "2.0.0",
        scalaVersion = Some("3.8.1")
      )
    )

    IO.withTemporaryDirectory { dir =>
      val lockFile = new File(dir, "test.lock")
      val writeResult = LockFile.write(lockFile, lockData)
      assert(writeResult.isRight, s"Write failed: ${writeResult.left.getOrElse("")}")

      val readResult = LockFile.read(lockFile)
      assert(readResult.isRight, s"Read failed: ${readResult.left.getOrElse("")}")

      val readData = readResult.toOption.get
      assert(readData.version == lockData.version)
      assert(readData.buildClock == lockData.buildClock)
      assert(readData.configurations.size == 1)
      assert(readData.configurations.head.name == "compile")
      assert(readData.configurations.head.dependencies.size == 1)
      assert(readData.configurations.head.dependencies.head.organization == "org.scala-lang")
      assert(readData.configurations.head.dependencies.head.version == "2.13.16")
      assert(readData.metadata.sbtVersion == "2.0.0")
      assert(readData.metadata.scalaVersion == Some("3.8.1"))
    }
  }

  test("LockFile.read returns Left for non-existent file") {
    val result = LockFile.read(new File("/nonexistent/path/lock.json"))
    assert(result.isLeft)
  }

  test("LockFile.read returns Left for invalid JSON") {
    IO.withTemporaryDirectory { dir =>
      val lockFile = new File(dir, "invalid.lock")
      IO.write(lockFile, "not valid json")
      val result = LockFile.read(lockFile)
      assert(result.isLeft)
    }
  }

  test("DependencyLock with classifier") {
    val dep = DependencyLock(
      organization = "org.example",
      name = "lib",
      version = "1.0.0",
      configuration = "compile",
      classifier = Some("sources"),
      tpe = "jar",
      transitives = Vector.empty,
      artifacts = Vector.empty
    )

    val lockData = LockFileData(
      version = "1.0",
      buildClock = "test",
      configurations = Vector(ConfigurationLock("compile", Vector(dep))),
      metadata = LockFileMetadata("2.0.0", None)
    )

    IO.withTemporaryDirectory { dir =>
      val lockFile = new File(dir, "test.lock")
      LockFile.write(lockFile, lockData)
      val readData = LockFile.read(lockFile).toOption.get
      assert(readData.configurations.head.dependencies.head.classifier == Some("sources"))
    }
  }

  test("cacheFileToOriginalUrl converts cache file URL to HTTP URL") {
    IO.withTemporaryDirectory { cacheDir =>
      val fileUrl =
        s"file:${cacheDir.getAbsolutePath}/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.12/scala-library-2.13.12.jar"
      val result = CoursierDependencyResolution.cacheFileToOriginalUrl(fileUrl, cacheDir)
      assert(
        result == "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.12/scala-library-2.13.12.jar"
      )
    }
  }

  test("cacheFileToOriginalUrl handles non-matching paths with CSR_CACHE placeholder") {
    IO.withTemporaryDirectory { cacheDir =>
      val fileUrl = "file:/some/other/path/artifact.jar"
      val result = CoursierDependencyResolution.cacheFileToOriginalUrl(fileUrl, cacheDir)
      assert(result == "${CSR_CACHE}/some/other/path/artifact.jar")
    }
  }

  test("cacheFileToOriginalUrl preserves non-file URLs") {
    IO.withTemporaryDirectory { cacheDir =>
      val httpUrl =
        "https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.13.12/scala-library-2.13.12.jar"
      val result = CoursierDependencyResolution.cacheFileToOriginalUrl(httpUrl, cacheDir)
      assert(result == httpUrl)
    }
  }
}
