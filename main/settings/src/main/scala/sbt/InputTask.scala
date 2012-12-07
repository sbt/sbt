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
	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def static[T](p: Parser[Task[T]]): InputTask[T] = free(_ => p)

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def static[I,T](p: Parser[I])(c: I => Task[T]): InputTask[T] = static(p map c)

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def free[T](p: State => Parser[Task[T]]): InputTask[T] = new InputStatic[T](p)

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def free[I,T](p: State => Parser[I])(c: I => Task[T]): InputTask[T] = free(s => p(s) map c)

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def separate[I,T](p: State => Parser[I])(action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
		separate(Def value p)(action)

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def separate[I,T](p: Initialize[State => Parser[I]])(action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
		p.zipWith(action)((parser, act) => free(parser)(act))

	private[sbt] lazy val inputMap: Task[Map[AnyRef,Any]] = mktask { error("Internal sbt error: input map not substituted.") }

	@deprecated("Use the non-overloaded `create` or the `Def.inputTask` macro.", "0.13.0")
	def apply[I,T](p: Initialize[State => Parser[I]])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
		create(p)(action)

	// This interface allows the Parser to be constructed using other Settings, but not Tasks (which is desired).
	// The action can be constructed using Settings and Tasks and with the parse result injected into a Task.
	// This is the ugly part, requiring hooks in Load.finalTransforms and Aggregation.applyDynamicTasks
	//  to handle the dummy task for the parse result.
	// However, this results in a minimal interface to the full capabilities of an InputTask for users
	def create[I,T](p: Initialize[State => Parser[I]])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
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

	/** A dummy parser that consumes no input and produces nothing useful (unit).*/
	def emptyParser: Initialize[State => Parser[Unit]] = parserAsInput(complete.DefaultParsers.success(()))

	/** Implementation detail that is public because it is used by a macro.*/
	def parserAsInput[T](p: Parser[T]): Initialize[State => Parser[T]] = Def.valueStrict(Types.const(p))

	/** Implementation detail that is public because it is used y a macro.*/
	def initParserAsInput[T](i: Initialize[Parser[T]]): Initialize[State => Parser[T]] = i(Types.const)

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def apply[I,T](p: State => Parser[I])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
		apply(Def.value(p))(action)
}
