/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import org.scalatest.FlatSpec
import sbt.internal.util.{ AttributeKey, AttributeMap }
import sbt.io.syntax.file
import scala.annotation.nowarn

class ScopeDisplaySpec extends FlatSpec {
  val project = ProjectRef(file("foo/bar"), "bar")
  val mangledName = "bar_slash_blah_blah_blah"

  @nowarn
  val scopedKey = Def.ScopedKey(Scope.Global in project, AttributeKey[Task[String]](mangledName))
  val am = AttributeMap.empty.put(Scope.customShowString, "blah")
  val sanitizedKey = scopedKey.copy(scope = scopedKey.scope.copy(extra = Select(am)))
  "Def.displayRelative2" should "display mangled name" in {
    assert(Def.showRelativeKey2(project, None).show(scopedKey) == mangledName)
  }
  it should "display sanitized name with extra setting" in {
    assert(Def.showRelativeKey2(project, None).show(sanitizedKey) == "blah")
  }
  "Scope.display" should "display the full scope" in {
    val full = Scope.display(
      scopedKey.scope,
      "/",
      (ref: Reference) =>
        ref match {
          case ProjectRef(_, n) => n
          case _                => ???
        }
    )
    assert(full == "bar /")
  }
  it should "display the sanitized scope" in {
    val string = Scope.display(
      sanitizedKey.scope,
      "/",
      (ref: Reference) =>
        ref match {
          case ProjectRef(_, n) => n
          case _                => ???
        }
    )
    assert(string == "blah")
  }

  behavior of "Def.displayRelative2"

  val b1 = project.build
  val b2 = sbt.io.IO.toURI(file("other"))

  val p1 = project
  val p2 = ProjectRef(b1, "baz")
  val p3 = ProjectRef(b2, "qux")

  private def disp(r: Reference) = Def.displayRelative2(current = p1, r)

  it should "ProjectRef curr proj" in assert(disp(p1) == "")
  it should "ProjectRef same build" in assert(disp(p2) == "baz /")
  it should "ProjectRef diff build" in assert(disp(p3) == """ProjectRef(uri("other"), "qux") /""")
  it should "BuildRef same build" in assert(disp(BuildRef(b1)) == "ThisBuild /")
  it should "BuildRef diff build" in assert(disp(BuildRef(b2)) == "{other} /")
  it should "RootProject same build" in assert(disp(RootProject(b1)) == "<root> /")
  it should "RootProject diff build" in assert(disp(RootProject(b2)) == "{other }<root> /")
  it should "LocalProject curr proj" in assert(disp(LocalProject(p1.project)) == "")
  it should "LocalProject diff proj" in assert(disp(LocalProject(p2.project)) == "baz /")
  it should "ThisBuild" in assert(disp(ThisBuild) == "ThisBuild /")
  it should "LocalRootProject" in assert(disp(LocalRootProject) == "<root> /")
  it should "ThisProject" in assert(disp(ThisProject) == "<this> /")
}
