/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah
 */
package sbt

import Types._

/** Represents a task node in a format understood by the task evaluation engine Execute.
* Heterogenous inputs (Mixed, tuple) and homogoneous (Uniform, sequence) are defined and consumed separately.
*
* @tparam A the task type
* @tparam T the type computed by this node */
trait Node[A[_], T]
{
	type Mixed <: HList
	type Uniform

	val mixedIn: KList[A, Mixed]
	val uniformIn: Seq[A[Uniform]]

	/** Computes the result of this task given the results from the inputs. */
	def work(mixed: KList[Result, Mixed], uniform: Seq[Result[Uniform]]): Either[A[T], T]
}
