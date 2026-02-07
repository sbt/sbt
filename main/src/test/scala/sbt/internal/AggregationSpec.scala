/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

import java.net.URI
import sbt.{ BuildRef, Def, Scope }
import sbt.Def.ScopedKey
import sbt.ScopeAxis.{ Select, Zero }
import sbt.internal.TestBuild.{ Build, Env, Proj, Taskk }
import sbt.internal.util.AttributeKey
import sbt.librarymanagement.Configuration

object AggregationSpec extends verify.BasicTestSuite {
  val timing = Aggregation.timing(0, _: Long)

  test(
    "projectAggregates should return empty for BuildRef (ThisBuild-scoped keys do not aggregate, #5349)"
  ) {
    val buildURI = new URI("file", "///path/", null)
    val config = Configuration.of("Compile", "compile")
    val project = Proj("root", Nil, Seq(config))
    val build = Build(buildURI, Vector(project))
    val key = AttributeKey[String]("test")
    val task = Taskk(key, Nil)
    val env = Env(Vector(build), Vector(task))
    val scope = Scope(Select(BuildRef(buildURI)), Zero, Select(key), Zero)
    val settings = Seq(Def.setting(ScopedKey(scope, key), Def.value("v")))
    val structure = TestBuild.structure(env, settings, build.allProjects.head._1)
    val result =
      Aggregation.projectAggregates(Some(BuildRef(buildURI)), structure.extra, reverse = false)
    assert(result.isEmpty, s"BuildRef must not aggregate; got: $result")
  }

  test("timing should format total time properly") {
    assert(timing(101).startsWith("elapsed time: 0 s"))
    assert(timing(1000).startsWith("elapsed time: 1 s"))
    assert(timing(3000).startsWith("elapsed time: 3 s"))
    assert(timing(30399).startsWith("elapsed time: 30 s"))
    assert(timing(60399).startsWith("elapsed time: 60 s"))
    assert(timing(60699).startsWith("elapsed time: 61 s (0:01:01.0)"))
    assert(timing(303099).startsWith("elapsed time: 303 s (0:05:03.0)"))
    assert(timing(6003099).startsWith("elapsed time: 6003 s (01:40:03.0)"))
    assert(timing(96003099).startsWith("elapsed time: 96003 s (26:40:03.0)"))
  }

  test("timing should not emit special space characters") {
    assert(!timing(96003099).contains("\u202F"))
  }
}
