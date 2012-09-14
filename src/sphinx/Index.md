[Initialize]: http://harrah.github.com/xsbt/latest/api/sbt/Init$Initialize.html
[dependency]: http://harrah.github.com/xsbt/latest/api/sbt/ModuleID.html
[Process]: http://harrah.github.com/xsbt/latest/api/sbt/Process.html
[Process companion object]: http://harrah.github.com/xsbt/latest/api/sbt/Process$.html
[ProcessBuilder]: http://harrah.github.com/xsbt/latest/api/sbt/ProcessBuilder.html
[Parser]: http://harrah.github.com/xsbt/latest/api/sbt/complete/Parser.html
[Keys]: http://harrah.github.com/xsbt/latest/api/sbt/Keys$.html
[Scope]: http://harrah.github.com/xsbt/latest/api/sbt/Scope.html
[ModuleID]: http://harrah.github.com/xsbt/latest/api/sbt/ModuleID.html
[Configuration]: http://harrah.github.com/xsbt/latest/api/sbt/Configuration.html
[Artifact]: http://harrah.github.com/xsbt/latest/api/sbt/Artifact.html
[Resolver]: http://harrah.github.com/xsbt/latest/api/sbt/Resolver.html
[NameFilter]: http://harrah.github.com/xsbt/latest/api/sbt/NameFilter.html
[FileFilter]: http://harrah.github.com/xsbt/latest/api/sbt/FileFilter.html
[Setting]: http://harrah.github.com/xsbt/latest/api/sbt/Init$Setting.html
[SettingList]: http://harrah.github.com/xsbt/latest/api/sbt/Init$SettingList.html
[SettingsDefinition]: http://harrah.github.com/xsbt/latest/api/sbt/Init$SettingsDefinition.html
[Build]: http://harrah.github.com/xsbt/latest/api/sbt/Build.html
[Plugin]: http://harrah.github.com/xsbt/latest/api/sbt/Plugin.html
[Project]: http://harrah.github.com/xsbt/latest/api/sbt/Project.html
[RichFile]: http://harrah.github.com/xsbt/latest/api/sbt/RichFile.html
[PathFinder]: http://harrah.github.com/xsbt/latest/api/sbt/PathFinder.html
[SettingKey]: http://harrah.github.com/xsbt/latest/api/sbt/SettingKey.html
[InputKey]: http://harrah.github.com/xsbt/latest/api/sbt/InputKey.html
[TaskKey]: http://harrah.github.com/xsbt/latest/api/sbt/TaskKey.html
[ScopedSetting]: http://harrah.github.com/xsbt/latest/api/sbt/ScopedSetting.html
[ScopedInput]: http://harrah.github.com/xsbt/latest/api/sbt/ScopedInput.html
[ScopedTask]: http://harrah.github.com/xsbt/latest/api/sbt/ScopedTask.html
[InputTask]: http://harrah.github.com/xsbt/latest/api/sbt/InputTask.html
[State]: http://harrah.github.com/xsbt/latest/api/sbt/State.html
[Task]: http://harrah.github.com/xsbt/latest/api/sbt/Task.html

# Index

This is an index of common methods, types, and values you might find in an sbt build definition.
For command names, see [[Running|Getting Started Running]].
For available plugins, see [[sbt 0.10 plugins list]].

## Values and Types

### Dependency Management

* [ModuleID] is the type of a dependency definition.  See [[Library Management]].
* [Artifact] represents a single artifact (such as a jar or a pom) to be built and published.  See [[Library Management]] and [[Artifacts]].
* A [Resolver] can resolve and retrieve dependencies.  Many types of Resolvers can publish dependencies as well.  A repository is a closely linked idea that typically refers to the actual location of the dependencies.  However, sbt is not very consistent with this terminology and repository and resolver are occasionally used interchangeably.
* A [ModuleConfiguration] defines a specific resolver to use for a group of dependencies.
* A [Configuration] is a useful Ivy construct for grouping
  dependencies.  See [[Configurations]].  It is also used for
  [[scoping settings|Getting Started Scopes]].
* `Compile`, `Test`, `Runtime`, `Provided`, and `Optional` are predefined [[Configurations]].

### Settings and Tasks

* A [Setting] describes how to initialize a specific setting in the build.  It can use the values of other settings or the previous value of the setting being initialized.
* A [SettingsDefinition] is the actual type of an expression in a build.sbt.  This allows either a single [Setting] or a sequence of settings ([SettingList]) to be defined at once.  The types in a [[Full Configuration]] always use just a plain [Setting].
* [Initialize] describes how to initialize a setting using other settings, but isn't bound to a particular setting yet.  Combined with an initialization method and a setting to initialize, it produces a full [Setting].
* [TaskKey], [SettingKey], and [InputKey] are keys that represent a task or setting.  These are not the actual tasks, but keys that are used to refer to them.  They can be scoped to produce [ScopedTask], [ScopedSetting], and [ScopedInput].  These form the base types that the [[Settings]] implicits add methods to.
* [InputTask] parses and tab completes user input, producing a task to run.
* [Task] is the type of a task.  A task is an action that runs on demand.  This is in contrast to a setting, which is run once at project initialization.

### Process

* A [ProcessBuilder] is the type used to define a process.  It provides combinators for building up processes from smaller processes.
* A [Process] represents the actual forked process.
* The [Process companion object] provides methods for constructing primitive processes.

### Build Structure

* [Build] is the trait implemented for a [[Full Configuration]], which defines project relationships and settings.
* [Plugin] is the trait implemented for sbt [[Plugins]].
* [Project] is both a trait and a companion object that declares a single module in a build.  See [[Full Configuration]].
* [Keys] is an object that provides all of the built-in keys for settings and tasks.
* [State] contains the full state for a build.  It is mainly used by [[Commands]] and sometimes [[Input Tasks]].  See also [[Build State]].

## Methods

### Settings and Tasks

See the [[Getting Started Guide|Getting Started Basic Def]] for details.

* `:=`, `<<=`, `+=`, `++=`, `~=`, `<+=`, `<++=`  These construct a
  [Setting], which is the fundamental type in the
  [[settings|Getting Started Basic Def]] system.
* `map`  This defines a task initialization that uses other tasks
  or settings.  See
  [[more about settings|Getting Started More About Settings]].  It is a common name used for many other types in Scala, such as collections.
* `apply` This defines a setting initialization using other settings.  It is not typically written out.  See [[more about settings|Getting Started More About Settings]].  This is a common name in Scala.
* `in` specifies the [Scope] or part of the [Scope] of a setting
  being referenced.  See [[scopes|Getting Started Scopes]].

### File and IO

See [RichFile], [PathFinder], and [[Paths]] for the full documentation.

* `/`  When called on a single File, this is `new File(x,y)`.  For `Seq[File]`, this is applied for each member of the sequence..
* `*` and `**`  are methods for selecting children (`*`) or descendants (`**`) of a `File` or `Seq[File]` that match a filter.
* `|`, `||`, `&&`, `&`, `-`, and `--` are methods for combining filters, which are often used for selecting `File`s.  See [NameFilter] and [FileFilter].  Note that methods with these names also exist for other types, such as collections (like `Seq) and [Parser] (see [[Parsing Input]]).
* `x` Used to construct mappings from a `File` to another `File` or to a `String`.  See [[Mapping Files]].
* `get` forces a [PathFinder] (a call-by-name data structure) to a strict `Seq[File]` representation.  This is a common name in Scala, used by types like `Option`.

### Dependency Management 

See [[Library Management]] for full documentation.

* `%` This is used to build up a [ModuleID].  
* `%%` This is similar to `%` except that it identifies a dependency that has been [[cross built|Cross Build]].
* `from` Used to specify the fallback URL for a dependency
* `classifier` Used to specify the classifier for a dependency.
* `at` Used to define a Maven-style resolver.
* `intransitive` Marks a [dependency] or [Configuration] as being intransitive.
* `hide` Marks a [Configuration] as internal and not to be included in the published metadata.

### Parsing

These methods are used to build up [Parser]s from smaller [Parser]s.  They closely follow the names of the standard library's parser combinators.  See [[Parsing Input]] for the full documentation.  These are used for [[Input Tasks]] and [[Commands]].

* `~`, `~>`, `<~`  Sequencing methods.
* `??`, `?`  Methods for making a Parser optional. `?` is postfix.
* `id` Used for turning a Char or String literal into a Parser.  It is generally used to trigger an implicit conversion to a Parser.
* `|`, `||`  Choice methods.   These are common method names in Scala.
* `^^^` Produces a constant value when a Parser matches.
* `+`, `*` Postfix repetition methods.    These are common method names in Scala.
* `map`, `flatMap` Transforms the result of a Parser.  These are common method names in Scala.
* `filter` Restricts the inputs that a Parser matches on.  This is a common method name in Scala.
* `-`  Prefix negation.  Only matches the input when the original parser doesn't match the input.
* `examples`, `token` Tab completion
* `!!!` Provides an error message to use when the original parser doesn't match the input.

### Processes

These methods are used to [[fork external processes|Process]].  Note that this API has been included in the Scala standard library for version 2.9.
[ProcessBuilder] is the builder type and [Process] is the type representing the actual forked process.
The methods to combine processes start with `#` so that they share the same precedence.

* `run`, `!`, `!!`, `!<`, `lines`, `lines_!` are different ways to start a process once it has been defined.  The `lines` variants produce a `Stream[String]` to obtain the output lines.
* `#<`, `#<<`, `#>` are used to get input for a process from a source or send the output of a process to a sink.
* `#|`  is used to pipe output from one process into the input of another.
* `#||`, `#&&`, `###` sequence processes in different ways.
