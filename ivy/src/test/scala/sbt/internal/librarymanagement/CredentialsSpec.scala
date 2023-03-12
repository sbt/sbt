package sbt.internal.librarymanagement

import sbt.librarymanagement.ivy.Credentials

import java.io.File
import java.nio.file.Files

import org.scalatest.funsuite.AnyFunSuite

class CredentialsSpec extends AnyFunSuite {

  test("load credential file without authentication") {
    val credentialsFile = File.createTempFile("credentials", "tmp")

    val content =
      """|host=example.org
         |user=username
         |password=password""".stripMargin

    Files.write(credentialsFile.toPath(), content.getBytes())

    val Right(credentials) = Credentials.loadCredentials(credentialsFile): @unchecked

    assert(credentials.realm == null)

    credentialsFile.delete()
  }

  test("DirectCredentials.toString") {
    assert(
      Credentials(
        realm = null,
        host = "example.org",
        userName = "username",
        passwd = "password"
      ).toString ==
        """DirectCredentials(null, "example.org", "username", ****)"""
    )

    assert(
      Credentials(
        realm = "realm",
        host = "example.org",
        userName = "username",
        passwd = "password"
      ).toString ==
        """DirectCredentials("realm", "example.org", "username", ****)"""
    )
  }
}
