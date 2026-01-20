package lmcoursier

import lmcoursier.internal._
import org.scalatest.funsuite.AnyFunSuite
import java.io.File
import java.time.Instant
import sbt.io.IO

class LockFileSpec extends AnyFunSuite {

  test("LockFileData serialization round-trip") {
    val lockData = LockFileData(
      version = "1.0",
      buildClock = "abc123",
      configurations = Seq(
        ConfigurationLock(
          name = "compile",
          dependencies = Seq(
            DependencyLock(
              organization = "org.scala-lang",
              name = "scala-library",
              version = "2.13.16",
              configuration = "compile",
              classifier = None,
              `type` = "jar",
              transitives = Seq("org.scala-lang:scala-library:2.13.16")
            )
          )
        )
      ),
      metadata = LockFileMetadata(
        sbtVersion = "2.0.0",
        scalaVersion = Some("3.7.4"),
        timestamp = Instant.parse("2026-01-20T15:00:00Z")
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
      assert(readData.metadata.scalaVersion == Some("3.7.4"))
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
      `type` = "jar",
      transitives = Seq.empty
    )

    val lockData = LockFileData(
      version = "1.0",
      buildClock = "test",
      configurations = Seq(ConfigurationLock("compile", Seq(dep))),
      metadata = LockFileMetadata("2.0.0", None, Instant.now())
    )

    IO.withTemporaryDirectory { dir =>
      val lockFile = new File(dir, "test.lock")
      LockFile.write(lockFile, lockData)
      val readData = LockFile.read(lockFile).toOption.get
      assert(readData.configurations.head.dependencies.head.classifier == Some("sources"))
    }
  }
}
