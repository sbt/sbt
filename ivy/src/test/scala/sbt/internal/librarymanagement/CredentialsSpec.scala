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

    val Right(credentials) = Credentials.loadCredentials(credentialsFile)

    assert(credentials.realm == null)

    credentialsFile.delete()
  }
}
