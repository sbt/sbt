/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package testpkg

import java.net.URI

import sbt.{ Result => _, _ }
import sbt.Def._
import sbt.internal.TestBuild
import sbt.internal.TestBuild._
import sbt.internal.util.AttributeKey
import sbt.librarymanagement.Configuration
import hedgehog._
import hedgehog.runner._
import _root_.sbt.internal.util.complete.Parser

object CompletionSpec extends Properties {
  override def tests: List[Test] =
    List(
      property("can complete any build", TestBuild.uriGen.forAll.map { uri =>
        complete(buildURI = uri, line = "{", expected = "{" + uri.toString + "}")
      }),
      property("can complete any project", TestBuild.nonEmptyId.forAll.map { id =>
        complete(projectID = id, line = id.head.toString, expected = id)
      }),
      // property(
      //   "can complete any configuration",
      //   TestBuild.nonEmptyId.forAll.map { name =>
      //     val cap = name.capitalize
      //     complete(configName = name, line = cap.head.toString, expected = cap)
      //   }
      // ),
      // property("can complete any attribute", TestBuild.kebabIdGen.forAll.map { name =>
      //   complete(attributeName = name, line = name.head.toString, expected = name)
      // })
    )

  private def complete(
      buildURI: URI = new URI("file", "///path/", null),
      projectID: String = "p",
      configName: String = "compile",
      attributeName: String = "a",
      line: String,
      expected: String,
  ): Result = {
    val attributeKey = AttributeKey[String](attributeName)
    val scope = Scope(
      Select(BuildRef(buildURI)),
      Select(ConfigKey(configName)),
      Select(attributeKey),
      Zero
    )
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
    // val scopedKey = ScopedKey(scope, attributeKey)
    // val string = displayMasked(scopedKey, ScopeMask())
    val parser = makeParser(structure)
    val cs = Parser.completions(parser, line, 10)

    Result
      .assert(
        cs.get.exists(c => (line + c.append) == expected || (line + c.append) == expected + "/")
      )
      .log(s"line: $line")
      .log(s"completions: ${cs.get.map(_.append)}")
      .log(s"structure: $structure")
  }
}
