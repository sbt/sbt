/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt

import Project._
import sbt.internal.util.Types.idFun
import sbt.internal.TestBuild._
import sbt.librarymanagement.Configuration
import org.scalacheck._
import Prop._
import Gen._

object Delegates extends Properties("delegates") {
  property("generate non-empty configs") = forAll { (c: Seq[Configuration]) =>
    c.nonEmpty
  }
  property("generate non-empty tasks") = forAll { (t: Seq[Taskk]) =>
    t.nonEmpty
  }

  property("no duplicate scopes") = forAll { (keys: Keys) =>
    allDelegates(keys) { (_, ds) =>
      ds.distinct.size == ds.size
    }
  }
  property("delegates non-empty") = forAll { (keys: Keys) =>
    allDelegates(keys) { (_, ds) =>
      ds.nonEmpty
    }
  }

  property("An initially Zero axis is Zero in all delegates") = allAxes(alwaysZero)

  property("Projects precede builds precede Zero") = forAll { (keys: Keys) =>
    allDelegates(keys) { (scope, ds) =>
      val projectAxes = ds.map(_.project)
      val nonProject = projectAxes.dropWhile {
        case Select(_: ProjectRef) => true; case _ => false
      }
      val global = nonProject.dropWhile { case Select(_: BuildRef) => true; case _ => false }
      global forall { _ == Zero }
    }
  }
  property("Initial scope present with all combinations of Global axes") = allAxes(
    globalCombinations)

  property("initial scope first") = forAll { (keys: Keys) =>
    allDelegates(keys) { (scope, ds) =>
      ds.head == scope
    }
  }
  property("global scope last") = forAll { (keys: Keys) =>
    allDelegates(keys) { (_, ds) =>
      ds.last == Scope.GlobalScope
    }
  }

  def allAxes(f: (Scope, Seq[Scope], Scope => ScopeAxis[_]) => Prop): Prop = forAll {
    (keys: Keys) =>
      allDelegates(keys) { (s, ds) =>
        all(f(s, ds, _.project), f(s, ds, _.config), f(s, ds, _.task), f(s, ds, _.extra))
      }
  }
  def allDelegates(keys: Keys)(f: (Scope, Seq[Scope]) => Prop): Prop =
    all(keys.scopes map { scope =>
      val delegates = keys.env.delegates(scope)
      ("Scope: " + Scope.display(scope, "_")) |:
        ("Delegates:\n\t" + delegates.map(scope => Scope.display(scope, "_")).mkString("\n\t")) |:
        f(scope, delegates)
    }: _*)
  def alwaysZero(s: Scope, ds: Seq[Scope], axis: Scope => ScopeAxis[_]): Prop =
    (axis(s) != Zero) ||
      all(ds map { d =>
        (axis(d) == Zero): Prop
      }: _*)
  def globalCombinations(s: Scope, ds: Seq[Scope], axis: Scope => ScopeAxis[_]): Prop = {
    val mods = List[Scope => Scope](_.copy(project = Zero),
                                    _.copy(config = Zero),
                                    _.copy(task = Zero),
                                    _.copy(extra = Zero))
    val modAndIdent = mods.map(_ :: idFun[Scope] :: Nil)

    def loop(cur: Scope, acc: List[Scope], rem: List[Seq[Scope => Scope]]): Seq[Scope] =
      rem match {
        case Nil => acc
        case x :: xs =>
          x flatMap { mod =>
            val s = mod(cur)
            loop(s, s :: acc, xs)
          }
      }
    all(loop(s, Nil, modAndIdent).map(ds contains _: Prop): _*)
  }
}
