/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import scala.util.control.NonFatal
import org.scalacheck._
import Prop._
import Project.project
import java.io.File

class ProjectDefs {
  lazy val p = project

  val x = project

  // should not compile
  // def y = project

  val z = project in new File("dir")

  val a: Project = project

  lazy val aa: Project = project
}

object ProjectMacro extends Properties("ProjectMacro") {
  lazy val pd = new ProjectDefs
  import pd._

  def secure(f: => Prop): Prop =
    try {
      Prop.secure(f)
    } catch {
      case NonFatal(e) =>
        e.printStackTrace
        throw e
    }

  property("Explicit type on lazy val supported") = secure {
    check(aa, "aa", "aa")
  }

  property("Explicit type on val supported") = secure {
    check(a, "a", "a")
  }

  property("lazy vals supported") = secure {
    check(p, "p", "p")
  }

  property("plain vals supported") = secure {
    check(x, "x", "x")
  }

  property("Directory overridable") = secure {
    check(z, "z", "dir")
  }

  def check(p: Project, id: String, dir: String): Prop = {
    s"Expected id: $id" |:
      s"Expected dir: $dir" |:
      s"Actual id: ${p.id}" |:
      s"Actual dir: ${p.base}" |:
      (p.id == id) &&
    (p.base.getName == dir)
  }
}
