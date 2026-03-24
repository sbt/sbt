/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import hedgehog.*
import hedgehog.runner.*

import java.net.URI
import scala.jdk.CollectionConverters.*

import _root_.sbt.Resolvers
import _root_.sbt.io.IO
import _root_.sbt.internal.BuildLoader.ResolveInfo

object ResolversVcsSecurityTest extends Properties:

  override def tests: List[Test] = List(
    example(
      "git resolver rejects fragment containing & before running VCS",
      testResolverRejects(Resolvers.git, vcsUri("git", "/repo.git", "main&evil"))
    ),
    example(
      "git resolver rejects fragment containing |",
      testResolverRejects(Resolvers.git, vcsUri("git", "/repo.git", "main|evil"))
    ),
    example(
      "git resolver rejects fragment containing ;",
      testResolverRejects(Resolvers.git, vcsUri("git", "/repo.git", "main;evil"))
    ),
    example(
      "mercurial resolver rejects fragment containing & before running VCS",
      testResolverRejects(Resolvers.mercurial, vcsUri("hg", "/repo", "main&evil"))
    ),
    example(
      "subversion resolver rejects fragment containing & before running VCS",
      testResolverRejects(Resolvers.subversion, vcsUri("svn", "/repo", "main&evil"))
    ),
    example(
      "git resolver accepts safe branch fragment and returns Some",
      testResolverAccepts(Resolvers.git, vcsUri("git", "/repo.git", "develop"))
    ),
    example(
      "ProcessBuilder passes VCS ref as a single argv element (no shell parsing)",
      testProcessBuilderKeepsMetacharactersInSingleArgument
    ),
  )

  private def vcsUri(scheme: String, path: String, fragment: String): URI =
    new URI(scheme, null, "example.com", -1, path, null, fragment)

  private def testResolverRejects(resolver: Resolvers.Resolver, uri: URI): Result =
    val staging = IO.createTemporaryDirectory
    try
      val info = new ResolveInfo(uri, staging, null, null)
      try
        resolver(info)
        Result.failure.log(s"expected IllegalArgumentException for $uri")
      catch case _: IllegalArgumentException => Result.success
    finally IO.delete(staging)

  private def testResolverAccepts(resolver: Resolvers.Resolver, uri: URI): Result =
    val staging = IO.createTemporaryDirectory
    try
      val info = new ResolveInfo(uri, staging, null, null)
      try
        resolver(info) match
          case Some(_) => Result.success
          case None    => Result.failure.log(s"expected Some for $uri")
      catch
        case e: IllegalArgumentException =>
          Result.failure.log(s"unexpected IllegalArgumentException for $uri: ${e.getMessage}")
    finally IO.delete(staging)

  private def testProcessBuilderKeepsMetacharactersInSingleArgument: Result =
    val argv =
      new ProcessBuilder("git", "fetch", "origin", "topic&injection").command().asScala.toList
    Result.assert(argv == List("git", "fetch", "origin", "topic&injection"))

end ResolversVcsSecurityTest
