/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

trait Rewrite[A[_]]
{
	def apply[T](node: A[T], rewrite: Rewrite[A]): A[T]
}
object Rewrite
{
	def Id[A[_]]: Rewrite[A] = new Rewrite[A] { def apply[T](node: A[T], rewrite: Rewrite[A]) = node }

	implicit def specificF[T](f: T => T): Rewrite[Const[T]#Apply] = new Rewrite[Const[T]#Apply] {
		def apply[S](node:T, rewrite: Rewrite[Const[T]#Apply]): T = f(node)
	}
	implicit def pToRewrite[A[_]](p: Param[A,A] => Unit): Rewrite[A] = toRewrite(Param.pToT(p))
	implicit def toRewrite[A[_]](f: A ~> A): Rewrite[A] = new Rewrite[A] {
		def apply[T](node: A[T], rewrite:Rewrite[A]) = f(node)
	}
	def compose[A[_]](a: Rewrite[A], b: Rewrite[A]): Rewrite[A] =
		new Rewrite[A] {
			def apply[T](node: A[T], rewrite: Rewrite[A]) =
				a(b(node, rewrite), rewrite)
		}
	implicit def rewriteOps[A[_]](outer: Rewrite[A]): RewriteOps[A] =
		new RewriteOps[A] {
			def ∙(g: A ~> A): Rewrite[A] = compose(outer, g)
			def andThen(g: A ~> A): Rewrite[A] = compose(g, outer)
			def ∙(g: Rewrite[A]): Rewrite[A] = compose(outer, g)
			def andThen(g: Rewrite[A]): Rewrite[A] = compose(g, outer)
		}
	def apply[A[_], T](value: A[T])(implicit rewrite: Rewrite[A]): A[T] = rewrite(value, rewrite)
}
trait RewriteOps[A[_]]
{
	def andThen(g: A ~> A): Rewrite[A]
	def ∙(g: A ~> A): Rewrite[A]
	def ∙(g: Rewrite[A]): Rewrite[A]
}