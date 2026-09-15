/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal.librarymanagement

import java.io.{ File, IOException }
import java.net.URI

import gigahorse.HeaderNames
import sbt.librarymanagement.Credentials

object GenericPublisherTest extends verify.BasicTestSuite:
  private val sourceFile = new File("artifact.jar")
  private val credentials =
    new Credentials.DirectCredentials("realm", "Repository.Example.com", "user", "password")

  test("authenticated PUTs send Basic credentials preemptively"):
    val request = GenericPublisher.httpPutRequest(
      URI.create("https://repository.example.com/artifact.jar").toURL,
      sourceFile,
      Some(credentials)
    )

    assert(request.headers(HeaderNames.AUTHORIZATION) == List("Basic dXNlcjpwYXNzd29yZA=="))
    assert(request.authOpt.isEmpty)
    assert(request.followRedirectsOpt.contains(false))

  test("authenticated PUTs require matching destination hosts"):
    val exception =
      try
        GenericPublisher.httpPutRequest(
          URI.create("https://other.example.com/artifact.jar").toURL,
          sourceFile,
          Some(credentials)
        )
        null
      catch case e: IOException => e

    assert(exception ne null)
    assert(exception.getMessage.contains("Refusing to send credentials"))

  test("authenticated PUTs require HTTPS"):
    val exception =
      try
        GenericPublisher.httpPutRequest(
          URI.create("http://repository.example.com/artifact.jar").toURL,
          sourceFile,
          Some(credentials)
        )
        null
      catch case e: IOException => e

    assert(exception ne null)
    assert(exception.getMessage.contains("non-HTTPS"))

  test("anonymous PUTs preserve HTTP and redirect behavior"):
    val request = GenericPublisher.httpPutRequest(
      URI.create("http://repository.example.com/artifact.jar").toURL,
      sourceFile,
      None
    )

    assert(!request.headers.contains(HeaderNames.AUTHORIZATION))
    assert(request.followRedirectsOpt.isEmpty)
end GenericPublisherTest
