/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.inc

import java.io.File
import java.util.concurrent.Callable

import org.scalatest.flatspec.AnyFlatSpec
import org.scalatest.matchers.should.Matchers
import sbt.internal.util.ConsoleLogger
import sbt.io.IO
import sbt.io.syntax.*
import xsbti.*

class ZincComponentManagerSpec extends AnyFlatSpec with Matchers {
  private val lock = new GlobalLock {
    override def apply[T](file: File, callable: Callable[T]): T = callable.call()
  }
  private val logger = ConsoleLogger()

  private def componentProvider(targetDir: File): ComponentProvider =
    ZincComponentCompiler.getDefaultComponentProvider(targetDir)

  "file" should "handle multiple files in the component directory by keeping the canonical one" in {
    IO.withTemporaryDirectory { base =>
      val componentDir = base / "components"
      IO.createDirectory(componentDir)
      val provider = componentProvider(componentDir)
      val manager = new ZincComponentManager(lock, provider, None, logger)
      val id = "org.scala-sbt-compiler-bridge_2.12-1.10.5-bin_2.12.20__65.0"
      val dir = componentDir / id
      IO.createDirectory(dir)
      IO.write(dir / s"$id.jar", "canonical")
      IO.write(dir / s"$id-1.10.5_20241130T035052.jar", "stamped")
      val result = manager.file(id)(IfMissing.fail)
      result.getName shouldBe s"$id.jar"
      IO.listFiles(dir) should have size 1
    }
  }

  "file" should "work when only one file exists" in {
    IO.withTemporaryDirectory { base =>
      val componentDir = base / "components"
      IO.createDirectory(componentDir)
      val provider = componentProvider(componentDir)
      val manager = new ZincComponentManager(lock, provider, None, logger)
      val id = "org.scala-sbt-compiler-bridge_2.12-1.10.5-bin_2.12.20__65.0"
      val dir = componentDir / id
      IO.createDirectory(dir)
      IO.write(dir / s"$id.jar", "canonical")
      val result = manager.file(id)(IfMissing.fail)
      result.getName shouldBe s"$id.jar"
    }
  }

  "update" should "normalize secondary cache file names to canonical names" in {
    IO.withTemporaryDirectory { base =>
      val componentDir = base / "components"
      IO.createDirectory(componentDir)
      val secondaryDir = base / "secondary"
      IO.createDirectory(secondaryDir / "org.scala-sbt")
      val provider = componentProvider(componentDir)
      val manager =
        new ZincComponentManager(lock, provider, Some(secondaryDir), logger)
      val id = "org.scala-sbt-compiler-bridge_2.12-1.10.5-bin_2.12.20__65.0"
      val stampedVersion = ZincComponentManager.stampedVersion
      val secondaryCacheFile =
        secondaryDir / "org.scala-sbt" / s"$id-$stampedVersion.jar"
      IO.write(secondaryCacheFile, "bridge content")
      val result = manager.file(id)(IfMissing.fail)
      result.getName shouldBe s"$id.jar"
      val allFiles = IO.listFiles(componentDir / id)
      allFiles should have size 1
      allFiles.head.getName shouldBe s"$id.jar"
    }
  }

  "update" should "not produce duplicate jars on repeated lookups from secondary cache" in {
    IO.withTemporaryDirectory { base =>
      val componentDir = base / "components"
      IO.createDirectory(componentDir)
      val secondaryDir = base / "secondary"
      IO.createDirectory(secondaryDir / "org.scala-sbt")
      val provider = componentProvider(componentDir)
      val manager =
        new ZincComponentManager(lock, provider, Some(secondaryDir), logger)
      val id = "org.scala-sbt-compiler-bridge_2.12-1.10.5-bin_2.12.20__65.0"
      val stampedVersion = ZincComponentManager.stampedVersion
      val secondaryCacheFile =
        secondaryDir / "org.scala-sbt" / s"$id-$stampedVersion.jar"
      IO.write(secondaryCacheFile, "bridge content")
      val result1 = manager.file(id)(IfMissing.fail)
      result1.getName shouldBe s"$id.jar"
      IO.listFiles(componentDir / id) should have size 1
      val result2 = manager.file(id)(IfMissing.fail)
      result2.getName shouldBe s"$id.jar"
      IO.listFiles(componentDir / id) should have size 1
    }
  }
}
