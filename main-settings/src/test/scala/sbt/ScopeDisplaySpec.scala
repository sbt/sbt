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

class ScopeDisplaySpec extends FlatSpec {
  val project = ProjectRef(file("foo/bar"), "bar")
  val mangledName = "bar_slash_blah_blah_blah"
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
}
