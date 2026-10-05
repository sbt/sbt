package lmcoursier.internal

import coursier.CoursierEnv
import coursier.core.{ Module, ModuleName, Organization, Resolution }
import coursier.error.ResolutionError.CantDownloadModule
import coursier.util.EnvValues
import coursier.version.VersionConstraint
import sbt.io.IO
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

import java.io.File

class ResolutionRunSpec extends AnyFunSuite with Matchers:

  private def cantDownload(errors: String*): CantDownloadModule =
    new CantDownloadModule(
      Resolution(),
      Module(Organization("org"), ModuleName("mod"), Map.empty),
      VersionConstraint("1.0"),
      errors.toSeq
    )

  test("503 is a transient resolution error"):
    val err = cantDownload(
      "Server returned HTTP response code: 503 for URL: https://repo.example.com/org/mod/1.0/mod-1.0.pom"
    )
    ResolutionRun.isTransientResolutionError(err) shouldBe true

  test("500 is a transient resolution error"):
    val err = cantDownload(
      "Server returned HTTP response code: 500 for URL: https://repo.example.com/org/mod/1.0/mod-1.0.pom"
    )
    ResolutionRun.isTransientResolutionError(err) shouldBe true

  test("connection timeout is a transient resolution error"):
    val err = cantDownload("Connection timed out")
    ResolutionRun.isTransientResolutionError(err) shouldBe true

  test("404 is not a transient resolution error"):
    val err = cantDownload(
      "Server returned HTTP response code: 404 for URL: https://repo.example.com/org/mod/1.0/mod-1.0.pom"
    )
    ResolutionRun.isTransientResolutionError(err) shouldBe false

  private val unset = EnvValues(None, None)

  private def withMirrorOfAll(f: File => Unit): Unit =
    IO.withTemporaryDirectory: dir =>
      IO.write(
        new File(dir, "settings.xml"),
        """<settings>
          |  <mirrors>
          |    <mirror>
          |      <id>internal</id>
          |      <url>https://nexus.example.com/repository/maven-public</url>
          |      <mirrorOf>*</mirrorOf>
          |    </mirror>
          |  </mirrors>
          |</settings>
          |""".stripMargin
      )
      f(dir)

  test("settings.xml mirrors are ignored unless the Maven settings are set explicitly"):
    withMirrorOfAll: dir =>
      val mavenHome = EnvValues(Some(dir.getAbsolutePath), None)
      assert(CoursierEnv.defaultMavenSettingsMirrors(unset, mavenHome, unset).nonEmpty)
      assert(ResolutionRun.mavenSettingsMirrors(unset, mavenHome, unset).isEmpty)

  test("settings.xml mirrors are read when the Maven settings are set explicitly"):
    withMirrorOfAll: dir =>
      val settings = new File(dir, "settings.xml").getAbsolutePath
      assert(
        ResolutionRun.mavenSettingsMirrors(EnvValues(Some(settings), None), unset, unset).size == 1
      )
      assert(
        ResolutionRun.mavenSettingsMirrors(EnvValues(None, Some(settings)), unset, unset).size == 1
      )
      val mavenHome = EnvValues(Some(dir.getAbsolutePath), None)
      assert(
        ResolutionRun
          .mavenSettingsMirrors(EnvValues(Some("true"), None), mavenHome, unset)
          .size == 1
      )
      assert(
        ResolutionRun.mavenSettingsMirrors(EnvValues(Some("false"), None), mavenHome, unset).isEmpty
      )
end ResolutionRunSpec
