[State]: http://harrah.github.com/xsbt/latest/api/sbt/State.html
[Command.scala]: http://harrah.github.com/xsbt/latest/sxr/Command.scala.html#10761

# Commands

# Introduction

There are three main aspects to commands:

1. The syntax used by the user to invoke the command, including:
    * Tab completion for the syntax
    * The parser to turn input into an appropriate data structure
2. The action to perform using the parsed data structure.  This action transforms the build [State].
3. Help provided to the user

In sbt, the syntax part, including tab completion, is specified with parser combinators.
If you are familiar with the parser combinators in Scala's standard library, these are very similar.
The action part is a function `(State, T) => State`, where `T` is the data structure produced by the parser.
See the [[Parsing Input]] page for how to use the parser combinators.

[State] provides access to the build state, such as all registered `Command`s, the remaining commands to execute, and all project-related information.  See [[Build State]] for details on State.

Finally, basic help information may be provided that is used by the `help` command to display command help.

# Defining a Command

A command combines a function `State => Parser[T]` with an action `(State, T) => State`.
The reason for `State => Parser[T]` and not simply `Parser[T]` is that often the current `State` is used to build the parser.
For example, the currently loaded projects (provided by `State`) determine valid completions for the `project` command.
Examples for the general and specific cases are shown in the following sections.

See [Command.scala] for the source API details for constructing commands.

## General commands

General command construction looks like:

```scala
val action: (State, T) => State = ...
val parser: State => Parser[T] = ...
val command: Command = Command("name")(parser)(action)
```

## No-argument commands

There is a convenience method for constructing commands that do not accept any arguments.

```scala
val action: State => State = ...
val command: Command = Command.command("name")(action)
```

## Single-argument command

There is a convenience method for constructing commands that accept a single argument with arbitrary content.

```scala
// accepts the state and the single argument
val action: (State, String) => State = ...
val command: Command = Command.single("name")(action)
```

## Multi-argument command

There is a convenience method for constructing commands that accept multiple arguments separated by spaces.

```scala
val action: (State, Seq[String]) => State = ...

// <arg> is the suggestion printed for tab completion on an argument
val command: Command = Command.args("name", "<arg>")(action)
```

# Full Example

The following example is a valid `project/Build.scala` that adds commands to a project.
To try it out:

1. Copy the following build definition into `project/Build.scala` for a new project.
2. Run sbt on the project.
3. Try out the `hello`, `hello-all`, `fail-if-true`, `color`, and `print-state` commands.
4. Use tab-completion and the code below as guidance.

```scala
import sbt._
import Keys._

// imports standard command parsing functionality
import complete.DefaultParsers._

object CommandExample extends Build
{
	// Declare a single project, adding several new commands, which are discussed below.
	lazy override val projects = Seq(root)
	lazy val root = Project("root", file(".")) settings(
		commands ++= Seq(hello, helloAll, failIfTrue, changeColor, printState)
	)

	// A simple, no-argument command that prints "Hi",
	//  leaving the current state unchanged.
	def hello = Command.command("hello") { state =>
		println("Hi!")
		state
	}


	// A simple, multiple-argument command that prints "Hi" followed by the arguments.
	//   Again, it leaves the current state unchanged.
	def helloAll = Command.args("hello-all", "<name>") { (state, args) =>
		println("Hi " + args.mkString(" "))
		state
	}


	// A command that demonstrates failing or succeeding based on the input
	def failIfTrue = Command.single("fail-if-true") {
		case (state, "true") => state.fail
		case (state, _) => state
	}


	// Demonstration of a custom parser.
	// The command changes the foreground or background terminal color
	//  according to the input.
	lazy val change = Space ~> (reset | setColor)
	lazy val reset = token("reset" ^^^ "\033[0m")
	lazy val color = token( Space ~> ("blue" ^^^ "4" | "green" ^^^ "2") )
	lazy val select = token( "fg" ^^^ "3" | "bg" ^^^ "4" )
	lazy val setColor = (select ~ color) map { case (g, c) => "\033[" + g + c + "m" }

	def changeColor = Command("color")(_ => change) { (state, ansicode) =>
		print(ansicode)
		state
	}


	// A command that demonstrates getting information out of State.
	def printState = Command.command("print-state") { state =>
		import state._
		println(definedCommands.size + " registered commands")
		println("commands to run: " + show(remainingCommands))
		println()

		println("original arguments: " + show(configuration.arguments))
		println("base directory: " + configuration.baseDirectory)
		println()

		println("sbt version: " + configuration.provider.id.version)
		println("Scala version (for sbt): " + configuration.provider.scalaProvider.version)
		println()

		val extracted = Project.extract(state)
		import extracted._
		println("Current build: " + currentRef.build)
		println("Current project: " + currentRef.project)
		println("Original setting count: " + session.original.size)
		println("Session setting count: " + session.append.size)

		state
	}

	def show[T](s: Seq[T]) =
		s.map("'" + _ + "'").mkString("[", ", ", "]")
}
```
