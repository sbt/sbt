/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt

import sbt.internal.util.Types.idFun
import sbt.internal.TestBuild._
import hedgehog.{ Result => Assert, _ }
import hedgehog.Result.{ all, assert, failure, success }
import hedgehog.runner._

object Delegates extends Properties {

  override def tests: List[Test] =
    List(
      property("generate non-empty configs", cGen.forAll.map { c =>
        assert(c.nonEmpty)
      }),
      property("generate non-empty tasks", tGen.forAll.map { t =>
        assert(t.nonEmpty)
      }),
      property("no duplicate scopes", keysGen.forAll.map { keys =>
        allDelegates(keys) { (_, ds) =>
          ds.distinct.size ==== ds.size
        }
      }),
      property("delegates non-empty", keysGen.forAll.map { keys =>
        allDelegates(keys) { (_, ds) =>
          assert(ds.nonEmpty)
        }
      }),
      property("An initially Zero axis is Zero in all delegates", allAxes(alwaysZero)),
      property(
        "Projects precede builds precede Zero",
        keysGen.forAll.map { keys =>
          allDelegates(keys) { (scope, ds) =>
            val projectAxes = ds.map(_.project)
            val nonProject = projectAxes.dropWhile {
              case Select(_: ProjectRef) => true; case _ => false
            }
            val global = nonProject.dropWhile { case Select(_: BuildRef) => true; case _ => false }
            all(global.map { _ ==== Zero }.toList)
          }
        }
      ),
      property(
        "Initial scope present with all combinations of Global axes",
        allAxes(
          (s, ds, _) => globalCombinations(s, ds)
        )
      ),
      property("initial scope first", keysGen.forAll.map { keys =>
        allDelegates(keys) { (scope, ds) =>
          ds.head ==== scope
        }
      }),
      property("global scope last", keysGen.forAll.map { keys =>
        allDelegates(keys) { (_, ds) =>
          ds.last ==== Scope.GlobalScope
        }
      }),
      property(
        "Project axis delegates to BuildRef then Zero",
        keysGen.forAll.map { keys =>
          allDelegates(keys) {
            (key, ds) =>
              key.project match {
                case Zero => success // filtering out of testing
                case Select(rr: ResolvedReference) =>
                  rr match {
                    case BuildRef(_) =>
                      assert(ds.indexOf(key) < ds.indexOf(key.copy(project = Zero)))
                    case ProjectRef(uri, _) =>
                      val buildScoped = key.copy(project = Select(BuildRef(uri)))
                      val idxKey = ds.indexOf(key)
                      val idxB = ds.indexOf(buildScoped)
                      val z = key.copy(project = Zero)
                      val idxZ = ds.indexOf(z)
                      (z ==== Scope.GlobalScope)
                        .or(
                          assert((idxKey < idxB) && (idxB < idxZ))
                            .log(s"idxKey = $idxKey; idxB = $idxB; idxZ = $idxZ")
                        )
                  }
                case Select(_) | This =>
                  failure.log(s"Scope's reference should be resolved, but was ${key.project}")
              }
          }
        }
      ),
      property(
        "Config axis delegates to parent configuration",
        keysGen.forAll.map { keys =>
          allDelegates(keys) {
            (key, ds) =>
              key.config match {
                case Zero => success
                case Select(config) if key.project.isSelect =>
                  val p = key.project.toOption.get
                  val r = keys.env.resolve(p)
                  keys.env.inheritConfig(r, config).headOption.fold(success) { parent =>
                    val idxKey = ds.indexOf(key)
                    val a = key.copy(config = Select(parent))
                    val idxA = ds.indexOf(a)
                    assert(idxKey < idxA)
                      .log(s"idxKey = $idxKey; a = $a; idxA = $idxA")
                  }
                case _ => success
              }
          }
        }
      )
    )

  def allAxes(f: (Scope, Seq[Scope], Scope => ScopeAxis[_]) => Assert): Property =
    keysGen.forAll.map { keys =>
      allDelegates(keys) { (s, ds) =>
        all(List(f(s, ds, _.project), f(s, ds, _.config), f(s, ds, _.task), f(s, ds, _.extra)))
      }
    }

  def allDelegates(keys: TestKeys)(f: (Scope, Seq[Scope]) => Assert): Assert =
    all(keys.scopes.map { scope =>
      val delegates = keys.env.delegates(scope)
      f(scope, delegates)
        .log("Scope: " + Scope.display(scope, "_"))
        .log("Delegates:\n\t" + delegates.map(scope => Scope.display(scope, "_")).mkString("\n\t"))
    }.toList)

  def alwaysZero(s: Scope, ds: Seq[Scope], axis: Scope => ScopeAxis[_]): Assert =
    assert(axis(s) != Zero).or(
      all(ds.map { d =>
        axis(d) ==== Zero
      }.toList)
    )

  def globalCombinations(s: Scope, ds: Seq[Scope]): Assert = {
    val mods = List[Scope => Scope](
      _.copy(project = Zero),
      _.copy(config = Zero),
      _.copy(task = Zero),
      _.copy(extra = Zero),
    )
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
    all(loop(s, Nil, modAndIdent).map(x => assert(ds contains x)).toList)
  }
}
