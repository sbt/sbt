/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.ScopeAxis.{ Select, Zero }
import hedgehog.*
import hedgehog.runner.*

import java.net.URI

object ScopeFilterOrderingSpec extends Properties {
  private val buildUri = new URI("file:///scope-filter-ordering/")

  override def tests: List[Test] =
    List(
      property(
        "all ordering is deterministic without ordering hints",
        projectIdsGen.forAll.map { projectIds =>
          val scopes = projectIds.map(toProjectRef).flatMap(projectScopes)
          val noHint: Scope => Option[Int] = _ => None
          val fromSet = ScopeFilter.orderedScopesForTests(scopes.toSet, noHint)
          val fromReverse = ScopeFilter.orderedScopesForTests(scopes.reverse, noHint)
          fromSet ==== fromReverse
        }
      ),
      property(
        "all ordering preserves explicit project ordering hints",
        projectIdsGen.forAll.map { projectIds =>
          val orderedProjects = projectIds.map(toProjectRef)
          val expected = orderedProjects.flatMap(projectScopes)
          val ordering = orderedProjects.zipWithIndex.toMap
          val byProjectOrder: Scope => Option[Int] = _.project match
            case Select(ref: ProjectRef) => ordering.get(ref)
            case _                       => None
          val actual = ScopeFilter.orderedScopesForTests(expected.reverse, byProjectOrder)
          actual ==== expected
        }
      )
    )

  private val projectIdGen: Gen[String] =
    for
      head <- Gen.char('a', 'z')
      tail <- Gen.string(Gen.alphaNum, Range.linear(0, 6))
    yield s"$head$tail"

  private val projectIdsGen: Gen[List[String]] =
    projectIdGen.list(Range.linear(1, 6)).filter { ids =>
      ids.distinct.size == ids.size
    }

  private def toProjectRef(id: String): ProjectRef =
    ProjectRef(buildUri, id)

  private def projectScopes(project: ProjectRef): List[Scope] =
    List(
      Scope(Select(project), Select(ConfigKey("compile")), Zero, Zero),
      Scope(Select(project), Select(ConfigKey("test")), Zero, Zero),
      Scope(Select(project), Zero, Zero, Zero)
    )
}
