/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

trait Node[A[_], T]
{
	type Mixed <: MList[A]
	type MixedResults = Mixed#Map[Result]
	type Uniform

	val mixedIn: Mixed
	val uniformIn: Seq[A[Uniform]]

	def work(mixed: MixedResults, uniform: Seq[Result[Uniform]]): Either[A[T], T]
}
