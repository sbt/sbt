/*
 * sbt
 * Copyright 2011 - 2017, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under BSD-3-Clause license (see LICENSE)
 */

package sbt.internal.util
package appmacro

import scala.reflect._
import macros._
import Types.idFun

abstract class Convert {
  def apply[T: c.WeakTypeTag](c: blackbox.Context)(nme: String, in: c.Tree): Converted[c.type]
  def asPredicate(c: blackbox.Context): (String, c.Type, c.Tree) => Boolean =
    (n, tpe, tree) => {
      val tag = c.WeakTypeTag(tpe)
      apply(c)(n, tree)(tag).isSuccess
    }
}
sealed trait Converted[C <: blackbox.Context with Singleton] {
  def isSuccess: Boolean
  def transform(f: C#Tree => C#Tree): Converted[C]
}
object Converted {
  def NotApplicable[C <: blackbox.Context with Singleton] = new NotApplicable[C]
  final case class Failure[C <: blackbox.Context with Singleton](position: C#Position,
                                                                 message: String)
      extends Converted[C] {
    def isSuccess = false
    def transform(f: C#Tree => C#Tree): Converted[C] = new Failure(position, message)
  }
  final class NotApplicable[C <: blackbox.Context with Singleton] extends Converted[C] {
    def isSuccess = false
    def transform(f: C#Tree => C#Tree): Converted[C] = this
  }
  final case class Success[C <: blackbox.Context with Singleton](tree: C#Tree,
                                                                 finalTransform: C#Tree => C#Tree)
      extends Converted[C] {
    def isSuccess = true
    def transform(f: C#Tree => C#Tree): Converted[C] = Success(f(tree), finalTransform)
  }
  object Success {
    def apply[C <: blackbox.Context with Singleton](tree: C#Tree): Success[C] =
      Success(tree, idFun)
  }
}
