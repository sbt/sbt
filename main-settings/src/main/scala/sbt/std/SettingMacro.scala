/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt
package std

import Def.Initialize
import sbt.internal.util.Types.{ Id, idFun }
import sbt.internal.util.AList
import sbt.internal.util.appmacro.{
  Convert,
  Converted,
  Instance,
  LinterDSL,
  MixedBuilder,
  MonadInstance
}

object InitializeInstance extends MonadInstance {
  type M[x] = Initialize[x]
  def app[K[L[x]], Z](in: K[Initialize], f: K[Id] => Z)(implicit a: AList[K]): Initialize[Z] =
    Def.app[K, Z](in)(f)(a)
  def map[S, T](in: Initialize[S], f: S => T): Initialize[T] = Def.map(in)(f)
  def flatten[T](in: Initialize[Initialize[T]]): Initialize[T] = Def.bind(in)(idFun[Initialize[T]])
  def pure[T](t: () => T): Initialize[T] = Def.pure(t)
}

import reflect.macros._

object InitializeConvert extends Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type] =
    nme match {
      case InputWrapper.WrapInitName                                 => convert[T](c)(in)
      case InputWrapper.WrapTaskName | InputWrapper.WrapInitTaskName => failTask[c.type](c)(in.pos)
      case InputWrapper.WrapPreviousName                             => failPrevious[c.type](c)(in.pos)
      case _                                                         => Converted.NotApplicable
    }

  private def convert[T: c.WeakTypeTag](c: blackbox.Context)(in: c.Tree): Converted[c.type] = {
    val i = c.Expr[Initialize[T]](in)
    val t = c.universe.reify(i.splice).tree
    Converted.Success(t)
  }

  private def failTask[C <: blackbox.Context with Singleton](c: C)(
      pos: c.Position): Converted[c.type] =
    Converted.Failure(pos, "A setting cannot depend on a task")
  private def failPrevious[C <: blackbox.Context with Singleton](c: C)(
      pos: c.Position): Converted[c.type] =
    Converted.Failure(pos, "A setting cannot depend on a task's previous value.")
}

object SettingMacro {
  import LinterDSL.{ Empty => EmptyLinter }
  def settingMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(t: c.Expr[T]): c.Expr[Initialize[T]] =
    Instance.contImpl[T, Id](c, InitializeInstance, InitializeConvert, MixedBuilder, EmptyLinter)(
      Left(t),
      Instance.idTransform[c.type])

  def settingDynMacroImpl[T: c.WeakTypeTag](c: blackbox.Context)(
      t: c.Expr[Initialize[T]]): c.Expr[Initialize[T]] =
    Instance.contImpl[T, Id](c, InitializeInstance, InitializeConvert, MixedBuilder, EmptyLinter)(
      Right(t),
      Instance.idTransform[c.type])
}
