[State]: http://harrah.github.com/xsbt/latest/api/sbt/State$.html
[Extracted]: http://harrah.github.com/xsbt/latest/api/sbt/Extracted.html
[Keys]: http://harrah.github.com/xsbt/latest/api/sbt/Keys$.html
[Eval]: http://harrah.github.com/xsbt/latest/api/sbt/compiler/Eval.html
[Scope]: http://harrah.github.com/xsbt/latest/api/sbt/Scope.html
[BuildStructure]: http://harrah.github.com/xsbt/latest/api/sbt/Load$$BuildStructure.html
[LoadedBuildUnit]: http://harrah.github.com/xsbt/latest/api/sbt/Load$$LoadedBuildUnit.html
[Structure.scala]: http://harrah.github.com/xsbt/latest/sxr/Structure.scala.html
[ResolvedProject]: http://harrah.github.com/xsbt/latest/api/sbt/ResolvedProject.html
[ProjectReferences]: http://harrah.github.com/xsbt/latest/api/sbt/ProjectReference.html

# State and actions

[State] is the entry point to all available information in sbt.
The key methods are:

* `definedCommands: Seq[Command]` returns all registered Command definitions
* `remainingCommands: Seq[String]` returns the remaining commands to be run
* `attributes: AttributeMap` contains generic data.

The action part of a command performs work and transforms `State`.
The following sections discuss `State => State` transformations.
As mentioned previously, a command will typically handle a parsed value as well: `(State, T) => State`.

## Command-related data

A Command can modify the currently registered commands or the commands to be executed.
This is done in the action part by transforming the (immutable) State provided to the command.
A function that registers additional power commands might look like:

```scala
val powerCommands: Seq[Command] = ...

val addPower: State => State =
  (state: State) =>
    state.copy(definedCommands =
      (state.definedCommands ++ powerCommands).distinct
    )
```

This takes the current commands, appends new commands, and drops duplicates.
Alternatively, State has a convenience method for doing the above:

```scala
val addPower2 = (state: State) => state ++ powerCommands
```

Some examples of functions that modify the remaining commands to execute:

```scala
val appendCommand: State => State =
  (state: State) =>
    state.copy(remainingCommands = state.remainingCommands :+ "cleanup")

val insertCommand: State => State =
  (state: State) =>
    state.copy(remainingCommands = "next-command" +: state.remainingCommands)
```

The first adds a command that will run after all currently specified commands run.
The second inserts a command that will run next.
The remaining commands will run after the inserted command completes.

To indicate that a command has failed and execution should not continue, return `state.fail`.

```scala
(state: State) => {
  val success: Boolean = ...
  if(success) state else state.fail
}
```

## Project-related data

Project-related information is stored in `attributes`.
Typically, commands won't access this directly but will instead use a convenience method to extract the most useful information:

```scala
val state: State
val extracted: Extracted = Project.extract(state)
import extracted._
```

[Extracted] provides:

* Access to the current build and project (`currentRef`)
* Access to initialized project setting data (`structure.data`)
* Access to session `Setting`s and the original, permanent settings from `.sbt` and `.scala` files (`session.append` and `session.original`, respectively)
* Access to the current [Eval] instance for evaluating Scala expressions in the build context.

## Project data
All project data is stored in `structure.data`, which is of type `sbt.Settings[Scope]`.
Typically, one gets information of type `T` in the following way:

```scala
val key: SettingKey[T]
val scope: Scope
val value: Option[T] = key in scope get structure.data
```

Here, a `SettingKey[T]` is typically obtained from [Keys] and is the same type that is used to define settings in `.sbt` files, for example.
[Scope] selects the scope the key is obtained for.
There are convenience overloads of `in` that can be used to specify only the required scope axes.  See [Structure.scala] for where `in` and other parts of the settings interface are defined.
Some examples:

```scala
import Keys._
val extracted: Extracted
import extracted._

// get name of current project
val nameOpt: Option[String] = name in currentRef get structure.data

// get the package options for the `test:package-src` task or Nil if none are defined
val pkgOpts: Seq[PackageOption] = packageOptions in (currentRef, Test, packageSrc) get structure.data getOrElse Nil
```

[BuildStructure] contains information about build and project relationships.
Key members are:

```scala
units: Map[URI, LoadedBuildUnit]
root: URI
```

A `URI` identifies a build and `root` identifies the initial build loaded.
[LoadedBuildUnit] provides information about a single build.
The key members of `LoadedBuildUnit` are:

```scala
// Defines the base directory for the build
localBase: File

// maps the project ID to the Project definition
defined: Map[String, ResolvedProject]
```

[ResolvedProject] has the same information as the `Project` used in a `project/Build.scala` except that [ProjectReferences] are resolved to `ProjectRef`s.

## Classpaths

Classpaths in sbt 0.10+ are of type `Seq[Attributed[File]]`.
This allows tagging arbitrary information to classpath entries.
sbt currently uses this to associate an `Analysis` with an entry.
This is how it manages the information needed for multi-project incremental recompilation.
It also associates the ModuleID and Artifact with managed entries (those obtained by dependency management).
When you only want the underlying `Seq[File]`, use `files`:

```scala
val attributedClasspath: Seq[Attribute[File]] = ...
val classpath: Seq[File] = attributedClasspath.files
```

## Running tasks
It can be useful to run a specific project task from a [[command|Commands]] (*not from another task*) and get its result.
For example, an IDE-related command might want to get the classpath from a project or a task might analyze the results of a compilation.
The relevant method is `Project.evaluateTask`, which has the following signature:

```scala
def evaluateTask[T](taskKey: ScopedKey[Task[T]], state: State,
  checkCycles: Boolean = false, maxWorkers: Int = ...): Option[Result[T]]
```

For example,

```scala
val eval: State => State = (state: State) => {

	// This selects the main 'compile' task for the current project.
	//   The value produced by 'compile' is of type inc.Analysis,
	//   which contains information about the compiled code.
	val taskKey = Keys.compile in Compile

	// Evaluate the task
	// None if the key is not defined
	// Some(Inc) if the task does not complete successfully (Inc for incomplete)
	// Some(Value(v)) with the resulting value
	val result: Option[Result[inc.Analysis]] = Project.evaluateTask(taskKey, state)
	// handle the result
	result match
	{
		case None => // Key wasn't defined.
		case Some(Inc(inc)) => // error detail, inc is of type Incomplete, use Incomplete.show(inc.tpe) to get an error message
		case Some(Value(v)) => // do something with v: inc.Analysis
	}
}
```

For getting the test classpath of a specific project, use this key:

```scala
val projectRef: ProjectRef = ...
val taskKey: Task[Seq[Attributed[File]]] =
  Keys.fullClasspath in (projectRef, Test)
```
