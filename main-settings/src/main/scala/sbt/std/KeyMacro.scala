/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package std

import scala.annotation.tailrec
import scala.reflect.macros._

import sbt.util.OptJsonWriter

private[sbt] object KeyMacro {
  def settingKeyImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      description: c.Expr[String]): c.Expr[SettingKey[T]] =
    keyImpl2[T, SettingKey[T]](c) { (name, mf, ojw) =>
      c.universe.reify { SettingKey[T](name.splice, description.splice)(mf.splice, ojw.splice) }
    }
  def taskKeyImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      description: c.Expr[String]): c.Expr[TaskKey[T]] =
    keyImpl[T, TaskKey[T]](c) { (name, mf) =>
      c.universe.reify { TaskKey[T](name.splice, description.splice)(mf.splice) }
    }
  def inputKeyImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      description: c.Expr[String]): c.Expr[InputKey[T]] =
    keyImpl[T, InputKey[T]](c) { (name, mf) =>
      c.universe.reify { InputKey[T](name.splice, description.splice)(mf.splice) }
    }

  def keyImpl[T: c.WeakTypeTag, S: c.WeakTypeTag](c: blackbox.Context)(
      f: (c.Expr[String], c.Expr[Manifest[T]]) => c.Expr[S]
  ): c.Expr[S] =
    f(getName(c), getImplicit[Manifest[T]](c))

  private def keyImpl2[T: c.WeakTypeTag, S: c.WeakTypeTag](c: blackbox.Context)(
      f: (c.Expr[String], c.Expr[Manifest[T]], c.Expr[OptJsonWriter[T]]) => c.Expr[S]
  ): c.Expr[S] =
    f(getName(c), getImplicit[Manifest[T]](c), getImplicit[OptJsonWriter[T]](c))

  private def getName[S: c.WeakTypeTag, T: c.WeakTypeTag](c: blackbox.Context): c.Expr[String] = {
    import c.universe._
    val enclosingValName = definingValName(
      c,
      methodName =>
        s"""$methodName must be directly assigned to a val, such as `val x = $methodName[Int]("description")`.""")
    c.Expr[String](Literal(Constant(enclosingValName)))
  }

  private def getImplicit[T: c.WeakTypeTag](c: blackbox.Context): c.Expr[T] = {
    import c.universe._
    c.Expr[T](c.inferImplicitValue(weakTypeOf[T]))
  }

  def definingValName(c: blackbox.Context, invalidEnclosingTree: String => String): String = {
    import c.universe.{ Apply => ApplyTree, _ }
    val methodName = c.macroApplication.symbol.name
    def processName(n: Name): String =
      n.decodedName.toString.trim // trim is not strictly correct, but macros don't expose the API necessary
    @tailrec def enclosingVal(trees: List[c.Tree]): String = {
      trees match {
        case vd @ ValDef(_, name, _, _) :: ts                => processName(name)
        case (_: ApplyTree | _: Select | _: TypeApply) :: xs => enclosingVal(xs)
        // lazy val x: X = <methodName> has this form for some reason (only when the explicit type is present, though)
        case Block(_, _) :: DefDef(mods, name, _, _, _, _) :: xs if mods.hasFlag(Flag.LAZY) =>
          processName(name)
        case _ =>
          c.error(c.enclosingPosition, invalidEnclosingTree(methodName.decodedName.toString))
          "<error>"
      }
    }
    enclosingVal(enclosingTrees(c).toList)
  }

  def enclosingTrees(c: blackbox.Context): Seq[c.Tree] =
    c.asInstanceOf[reflect.macros.runtime.Context]
      .callsiteTyper
      .context
      .enclosingContextChain
      .map(_.tree.asInstanceOf[c.Tree])
}
