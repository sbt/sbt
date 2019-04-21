/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

import java.net.URI

import sbt.Def._
import sbt._
import sbt.internal.TestBuild
import sbt.internal.TestBuild._
import sbt.internal.util.AttributeKey
import sbt.internal.util.complete.DefaultParsers
import sbt.librarymanagement.Configuration
import hedgehog._
import hedgehog.runner._

object ParserSpec extends Properties {
  override def tests: List[Test] =
    List(
      property("can parse any build", TestBuild.uriGen.forAll.map { uri =>
        parse(buildURI = uri)
      }),
      property("can parse any project", TestBuild.nonEmptyId.forAll.map { id =>
        parse(projectID = id)
      }),
      property("can parse any configuration", TestBuild.nonEmptyId.map(_.capitalize).forAll.map {
        name =>
          parse(configName = name)
      }),
      property("can parse any attribute", TestBuild.kebabIdGen.forAll.map { name =>
        parse(attributeName = name)
      })
    )

  private def parse(
      buildURI: URI = new java.net.URI("file", "///path/", null),
      projectID: String = "p",
      configName: String = "c",
      attributeName: String = "a"
  ) = {
    val attributeKey = AttributeKey[String](attributeName)
    val scope = Scope(
      Select(BuildRef(buildURI)),
      Select(ConfigKey(configName)),
      Select(attributeKey),
      Zero
    )
    val scopedKey = ScopedKey(scope, attributeKey)
    val config = Configuration.of(configName.capitalize, configName)
    val project = Proj(projectID, Nil, Seq(config))
    val projects = Vector(project)
    val build = Build(buildURI, projects)
    val builds = Vector(build)
    val task = Taskk(attributeKey, Nil)
    val tasks = Vector(task)
    val env = Env(builds, tasks)
    val settings = env.tasks.map { t =>
      Def.setting(ScopedKey(scope, t.key), Def.value("value"))
    }
    val structure = TestBuild.structure(env, settings, build.allProjects.head._1)
    val string = displayMasked(scopedKey, ScopeMask())
    val parser = makeParser(structure)
    val result = DefaultParsers.result(parser, string)
    val resultStr = result.fold(_ => "<parse error>", _.toString)
    (result ==== Right(scopedKey))
      .log(s"$string parsed back to $resultStr rather than $scopedKey")
  }
}
