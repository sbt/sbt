/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

trait Node[A[_], T]
{
	type Mixed <: HList
	type Uniform

	val mixedIn: KList[A, Mixed]
	val uniformIn: Seq[A[Uniform]]

	def work(mixed: KList[Result, Mixed], uniform: Seq[Result[Uniform]]): Either[A[T], T]
}
