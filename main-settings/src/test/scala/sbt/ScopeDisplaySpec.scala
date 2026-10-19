/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.ScopeAxis.Select
import sbt.internal.util.{ AttributeKey, AttributeMap }
import sbt.io.IO
import sbt.io.syntax.file
import hedgehog.*
import hedgehog.runner.*

object ScopeDisplaySpec extends Properties:
  override def tests: List[Test] = List(
    example(
      "Def.showRelativeKey2 should display mangled name",
      Def.showRelativeKey2(project, None).show(scopedKey) ==== mangledName
    ),
    example(
      "Def.showRelativeKey2 should display sanitized name with extra setting",
      Def.showRelativeKey2(project, None).show(sanitizedKey) ==== "blah"
    ),
    example(
      "Scope.display should display the full scope",
      Scope.display(scopedKey.scope, "/", showProjectName) ==== "bar /"
    ),
    example(
      "Scope.display should display the sanitized scope",
      Scope.display(sanitizedKey.scope, "/", showProjectName) ==== "blah"
    ),
  ) ++ displayRelativeCases.map: (name, ref, expected) =>
    example(
      s"Def.displayRelative2 should display $name",
      Def.displayRelative2(current = p1, ref) ==== expected
    )

  def displayRelativeCases: List[(String, Reference, String)] = List(
    ("ProjectRef curr proj", p1, ""),
    ("ProjectRef same build", p2, "baz /"),
    ("ProjectRef diff build", p3, """ProjectRef(uri("other"), "qux") /"""),
    ("BuildRef same build", BuildRef(b1), "ThisBuild /"),
    ("BuildRef diff build", BuildRef(b2), "{other} /"),
    ("RootProject same build", RootProject(b1), "<root> /"),
    ("RootProject diff build", RootProject(b2), "{other }<root> /"),
    ("LocalProject curr proj", LocalProject(p1.project), ""),
    ("LocalProject diff proj", LocalProject(p2.project), "baz /"),
    ("ThisBuild", ThisBuild, "ThisBuild /"),
    ("LocalRootProject", LocalRootProject, "<root> /"),
    ("ThisProject", ThisProject, "<this> /"),
  )

  val project = ProjectRef(file("foo/bar"), "bar")
  val mangledName = "bar_slash_blah_blah_blah"

  val scopedKey =
    Def.ScopedKey(Scope.Global.rescope(project), AttributeKey[Task[String]](mangledName))
  val am = AttributeMap.empty.put(Scope.customShowString, "blah")
  val sanitizedKey = scopedKey.copy(scope = scopedKey.scope.copy(extra = Select(am)))

  def showProjectName(ref: Reference): String = ref match
    case ProjectRef(_, n) => n
    case _                => sys.error(s"unexpected $ref")

  val b1 = project.build
  val b2 = IO.toURI(file("other"))

  val p1 = project
  val p2 = ProjectRef(b1, "baz")
  val p3 = ProjectRef(b2, "qux")
end ScopeDisplaySpec
