package sbt

	import complete.Parser
	import Def.{Initialize, ScopedKey}
	import std.TaskExtra.{task => mktask, _}
	import Task._
	import Types._

/** Parses input and produces a task to run.  Constructed using the companion object. */
sealed trait InputTask[T] {
	def mapTask[S](f: Task[T] => Task[S]): InputTask[S]
}
private final class InputStatic[T](val parser: State => Parser[Task[T]]) extends InputTask[T] {
	def mapTask[S](f: Task[T] => Task[S]) = new InputStatic(s => parser(s) map f)
}
private sealed trait InputDynamic[T] extends InputTask[T]
{ outer =>
	type Result
	def parser: State => Parser[Result]
	def defined: ScopedKey[_]
	def task: Task[T]
	def mapTask[S](f: Task[T] => Task[S]) = new InputDynamic[S] {
		type Result = outer.Result
		def parser = outer.parser
		def task = f(outer.task)
		def defined = outer.defined
	}
}
object InputTask
{
	def static[T](p: Parser[Task[T]]): InputTask[T] = free(_ => p)
	def static[I,T](p: Parser[I])(c: I => Task[T]): InputTask[T] = static(p map c)

	def free[T](p: State => Parser[Task[T]]): InputTask[T] = new InputStatic[T](p)
	def free[I,T](p: State => Parser[I])(c: I => Task[T]): InputTask[T] = free(s => p(s) map c)

	def separate[I,T](p: State => Parser[I])(action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
		separate(Def value p)(action)
	def separate[I,T](p: Initialize[State => Parser[I]])(action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
		p.zipWith(action)((parser, act) => free(parser)(act))
		
	private[sbt] lazy val inputMap: Task[Map[AnyRef,Any]] = mktask { error("Internal sbt error: input map not substituted.") }

	// This interface allows the Parser to be constructed using other Settings, but not Tasks (which is desired).
	// The action can be constructed using Settings and Tasks and with the parse result injected into a Task.
	// This is the ugly part, requiring hooks in Load.finalTransforms and Aggregation.applyDynamicTasks
	//  to handle the dummy task for the parse result.
	// However, this results in a minimal interface to the full capabilities of an InputTask for users
	def apply[I,T](p: Initialize[State => Parser[I]])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
	{
		val key: TaskKey[I] = Def.parseResult.asInstanceOf[TaskKey[I]]
		(p zip Def.resolvedScoped zipWith action(key)) { case ((parserF, scoped), act) =>
			new InputDynamic[T]
			{
				type Result = I
				def parser = parserF
				def task = act
				def defined = scoped
			}
		}
	}
	def apply[I,T](p: State => Parser[I])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
		apply(Def.value(p))(action)
}
