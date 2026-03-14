package lmcoursier.internal

import coursier.core.{ Module, ModuleName, Organization, Resolution }
import coursier.error.ResolutionError.CantDownloadModule
import coursier.version.VersionConstraint
import org.scalatest.funsuite.AnyFunSuite
import org.scalatest.matchers.should.Matchers

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
