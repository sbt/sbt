package sbt

	import complete.Parser
	import Def.{Initialize, ScopedKey}
	import std.TaskExtra.{task => mktask, _}
	import Task._
	import Types._

/** Parses input and produces a task to run.  Constructed using the companion object. */
sealed trait InputTask[T]
{ outer =>
	private[sbt] type Result
	private[sbt] def parser: State => Parser[Result]
	private[sbt] def defined: AttributeKey[Result]
	private[sbt] def task: Task[T]

	def mapTask[S](f: Task[T] => Task[S]) = new InputTask[S] {
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
	def free[T](p: State => Parser[Task[T]]): InputTask[T] = {
		val key = localKey[Task[T]]
		new InputTask[T] {
			type Result = Task[T]
			def parser = p
			def defined = key
			def task = getResult(key)
		}
	}

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def free[I,T](p: State => Parser[I])(c: I => Task[T]): InputTask[T] = free(s => p(s) map c)

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def separate[I,T](p: State => Parser[I])(action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
		separate(Def value p)(action)

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def separate[I,T](p: Initialize[State => Parser[I]])(action: Initialize[I => Task[T]]): Initialize[InputTask[T]] =
		p.zipWith(action)((parser, act) => free(parser)(act))

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def apply[I,T](p: Initialize[State => Parser[I]])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
	{
		create(p){ it =>
			val dummy = TaskKey(localKey[Task[I]])
			action(dummy) mapConstant subResultForDummy(dummy)
		}
	}

	@deprecated("Use `create` or the `Def.inputTask` macro.", "0.13.0")
	def apply[I,T](p: State => Parser[I])(action: TaskKey[I] => Initialize[Task[T]]): Initialize[InputTask[T]] =
		apply(Def.value(p))(action)

	// dummy task that will get replaced with the actual mappings from InputTasks to parse results
	private[sbt] lazy val inputMap: Task[AttributeMap] = mktask { error("Internal sbt error: input map not substituted.") }
	private[this] def getResult[T](key: AttributeKey[Task[T]]): Task[T] = inputMap flatMap { im =>
		im get key getOrElse error("Internal sbt error: could not get parser result.")
	}
	/** The proper solution is to have a Manifest context bound and accept slight source incompatibility,
	* The affected InputTask construction methods are all deprecated and so it is better to keep complete
	* compatibility.  Because the AttributeKey is local, it uses object equality and the manifest is not used. */
	private[this] def localKey[T]: AttributeKey[T] = AttributeKey.local[Unit].asInstanceOf[AttributeKey[T]]

	private[this] def subResultForDummy[I](dummy: TaskKey[I]) = 
		new (ScopedKey ~> Option) { def apply[T](sk: ScopedKey[T]) =
			if(sk.key eq dummy.key) {
				// sk.key: AttributeKey[T], dummy.key: AttributeKey[Task[I]]
				// (sk.key eq dummy.key) ==> T == Task[I] because AttributeKey is invariant
				Some(getResult(dummy.key).asInstanceOf[T])
			} else
				None
		}

	// This interface allows the Parser to be constructed using other Settings, but not Tasks (which is desired).
	// The action can be constructed using Settings and Tasks and with the parse result injected into a Task.
	// This requires Aggregation.applyDynamicTasks to inject an AttributeMap with the parse results.
	def create[I,T](p: Initialize[State => Parser[I]])(action: Initialize[Task[I]] => Initialize[Task[T]]): Initialize[InputTask[T]] =
	{
		val key = localKey[I] // TODO: AttributeKey.local[I]
		val result: Initialize[Task[I]] = (Def.resolvedScoped zipWith Def.valueStrict(InputTask.inputMap)){(scoped, imTask) =>
			imTask map { im => im get key getOrElse error("No parsed value for " + Def.displayFull(scoped) + "\n" + im) }
		}

		(p zipWith action(result)) { (parserF, act) =>
			new InputTask[T]
			{
				type Result = I
				def parser = parserF
				def task = act
				def defined = key
			}
		}
	}

	/** A dummy parser that consumes no input and produces nothing useful (unit).*/
	def emptyParser: Initialize[State => Parser[Unit]] = parserAsInput(complete.DefaultParsers.success(()))

	/** Implementation detail that is public because it is used by a macro.*/
	def parserAsInput[T](p: Parser[T]): Initialize[State => Parser[T]] = Def.valueStrict(Types.const(p))

	/** Implementation detail that is public because it is used y a macro.*/
	def initParserAsInput[T](i: Initialize[Parser[T]]): Initialize[State => Parser[T]] = i(Types.const)
}

