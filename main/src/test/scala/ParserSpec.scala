/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

import java.net.URI

import org.scalatest.prop.PropertyChecks
import org.scalatest.{ Matchers, PropSpec }
import sbt.Def._
import sbt._
import sbt.internal.TestBuild
import sbt.internal.TestBuild._
import sbt.internal.util.AttributeKey
import sbt.internal.util.complete.DefaultParsers
import sbt.librarymanagement.Configuration

class ParserSpec extends PropSpec with PropertyChecks with Matchers {

  property("can parse any build") {
    forAll(TestBuild.uriGen) { uri =>
      parse(buildURI = uri)
    }
  }

  property("can parse any project") {
    forAll(TestBuild.idGen) { id =>
      parse(projectID = id)
    }
  }

  property("can parse any configuration") {
    forAll(TestBuild.scalaIDGen) { name =>
      parse(configName = name)
    }
  }

  property("can parse any attribute") {
    forAll(TestBuild.lowerIDGen) { name =>
      parse(attributeName = name)
    }
  }

  private def parse(
      buildURI: URI = new java.net.URI("s", "p", null),
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
    val result = DefaultParsers.result(parser, string).left.map(_().toString)
    result shouldBe Right(scopedKey)
  }
}
