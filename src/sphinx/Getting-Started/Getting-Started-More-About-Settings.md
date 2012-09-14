[Keys]: http://harrah.github.com/xsbt/latest/sxr/Keys.scala.html "Keys.scala"
[ScopedSetting]: http://harrah.github.com/xsbt/latest/api/sbt/ScopedSetting.html

# More Kinds of Setting

[[Previous|Getting Started Scopes]] _Getting Started Guide page
8 of 14._ [[Next|Getting Started Library Dependencies]]


This page explains other ways to create a `Setting`, beyond the basic `:=`
method. It assumes you've read [[.sbt build definition|Getting Started Basic Def]] and [[scopes|Getting Started Scopes]].

## Refresher: Settings

[[Remember|Getting Started Basic Def]], a build definition creates a list of
`Setting`, which is then used to transform sbt's description of the build
(which is a map of key-value pairs). A `Setting` is a transformation with
sbt's earlier map as input and a new map as output. The new map becomes
sbt's new state.

Different settings transform the map in different
ways. [[Earlier|Getting Started Basic Def]], you read about the `:=` method.

The `Setting` which `:=` creates puts a fixed, constant value in the new,
transformed map. For example, if you transform a map with the setting
`name := "hello"` the new map has the string `"hello"` stored under the key
`name`.

Settings must end up in the master list of settings to do any good (all
lines in a `build.sbt` automatically end up in the list, but in a
[[.scala file|Getting Started Full Def]] you can get it wrong by
creating a `Setting` without putting it where sbt will find it).

## Appending to previous values: `+=` and `++=`

Replacement with `:=` is the simplest transformation, but keys have other
methods as well.  If the `T` in `SettingKey[T]` is a sequence, i.e. the key's value
type is a sequence, you can append to the sequence rather than replacing it.

 - `+=` will append a single element to the sequence.
 - `++=` will concatenate another sequence.

For example, the key `sourceDirectories in Compile` has a `Seq[File]` as its
value. By default this key's value would include `src/main/scala`.
If you wanted to also compile source code in a directory called `source`
(since you just have to be nonstandard), you could add that directory:

```scala
sourceDirectories in Compile += new File("source")
```

Or, using the `file()` function from the sbt package for convenience:

```scala
sourceDirectories in Compile += file("source")
```

(`file()` just creates a new `File`.)

You could use `++=` to add more than one directory at a time:

```scala
sourceDirectories in Compile ++= Seq(file("sources1"), file("sources2"))
```

Where `Seq(a, b, c, ...)` is standard Scala syntax to construct a sequence.

To replace the default source directories entirely, you use `:=` of
course:

```scala
sourceDirectories in Compile := Seq(file("sources1"), file("sources2"))
```

## Transforming a value: `~=`

What happens if you want to _prepend_ to `sourceDirectories in Compile`, or
filter out one of the default directories?

You can create a `Setting` that depends on the previous value of a key.

 - `~=` applies a function to the setting's previous value, producing a new
   value of the same type.

To modify `sourceDirectories in Compile`, you could use `~=` as follows:

```scala
// filter out src/main/scala
sourceDirectories in Compile ~= { srcDirs => srcDirs filter(!_.getAbsolutePath.endsWith("src/main/scala")) }
```

Here, `srcDirs` is a parameter to an anonymous function, and the old value
of `sourceDirectories in Compile` gets passed in to the anonymous
function. The result of this function becomes the new value of
`sourceDirectories in Compile`.

Or a simpler example:

```scala
// make the project name upper case
name ~= { _.toUpperCase }
```

The function you pass to the `~=` method will always have type `T
=> T`, if the key has type `SettingKey[T]` or `TaskKey[T]`. The
function transforms the key's value into another value of the same
type.

## Computing a value based on other keys' values: `<<=`

`~=` defines a new value in terms of a key's previously-associated
value. But what if you want to define a value in terms of _other_ keys'
values?

 - `<<=` lets you compute a new value using the value(s) of arbitrary other keys.

`<<=` has one argument, of type `Initialize[T]`. An `Initialize[T]` instance
is a computation which takes the values associated with a set of keys as
input, and returns a value of type `T` based on those other values. It
initializes a value of type `T`.

Given an `Initialize[T]`, `<<=` returns a `Setting[T]`, of course (just like
 `:=`, `+=`, `~=`, etc.).

### Trivial `Initialize[T]`: depending on one other key with `<<=`

All keys extend the `Initialize` trait already. So the simplest `Initialize`
is just a key:

```scala
// useless but valid
name <<= name
```

When treated as an `Initialize[T]`, a `SettingKey[T]` computes its
current value. So `name <<= name` sets the value of `name` to the
value that `name` already had.

It gets a little more useful if you set a key to a _different_ key. The keys
must have identical value types, though.

```scala
// name our organization after our project (both are SettingKey[String])
organization <<= name
```

(Note: this is how you alias one key to another.)

If the value types are not identical, you'll need to convert from
`Initialize[T]` to another type, like `Initialize[S]`. This is done with the
`apply` method on `Initialize`, like this:

```scala
// name is a Key[String], baseDirectory is a Key[File]
// name the project after the directory it's inside
name <<= baseDirectory.apply(_.getName)
```

`apply` is special in Scala and means you can invoke the object with
function syntax; so you could also write this:

```scala
name <<= baseDirectory(_.getName)
```

That transforms the value of `baseDirectory` using the function `_.getName`,
where the function `_.getName` takes a `File` and returns a
`String`. `getName` is a method on the standard `java.io.File` object.

### Settings with dependencies

In the setting `name <<= baseDirectory(_.getName)`, `name` will have a
_dependency_ on `baseDirectory`.  If you place the above in `build.sbt` and
run the sbt interactive console, then type `inspect name`, you should see
(in part):

```text
[info] Dependencies:
[info] 	*:base-directory
```

This is how sbt knows which settings depend on which other
settings. Remember that some settings describe tasks, so this approach also
creates dependencies between tasks.

For example, if you `inspect compile` you'll see it depends on another key
`compile-inputs`, and if you inspect `compile-inputs` it in turn depends on
other keys. Keep following the dependency chains and magic happens.  When
you type `compile` sbt automatically performs an `update`, for example. It
Just Works because the values required as inputs to the `compile`
computation require sbt to do the `update` computation first.

In this way, all build dependencies in sbt are _automatic_ rather than
explicitly declared. If you use a key's value in another computation, then
the computation depends on that key. It just works!

### Complex `Initialize[T]`: depending on multiple keys with `<<=`

To support dependencies on multiple other keys, sbt adds `apply` and
`identity` methods to tuples of `Initialize` objects. In Scala, you write a
tuple like `(1, "a")` (that one has type `(Int, String)`).

So say you have a tuple of three `Initialize` objects; its type would be
`(Initialize[A], Initialize[B], Initialize[C])`. The `Initialize` objects
could be keys, since all `SettingKey[T]` are also instances of `Initialize[T]`.

Here's a simple example, in this case all three keys are strings:

```scala
// a tuple of three SettingKey[String], also a tuple of three Initialize[String]
(name, organization, version)
```

The `apply` method on a tuple of `Initialize` takes a function as its
argument. Using each `Initialize` in the tuple, sbt computes a corresponding
value (the current value of the key). These values are passed in to the
function. The function then returns _one_ value, which is wrapped up in a
new `Initialize`. If you wrote it out with explicit types (Scala does not
require this), it would look like:

```scala
val tuple: (Initialize[String], Initialize[String], Initialize[String]) = (name, organization, version)
val combined: Initialize[String] = tuple.apply({ (n, o, v) =>
    "project " + n + " from " + o + " version " + v })
val setting: Setting[String] = name <<= combined
```

So each key is already an `Initialize`; but you can combine up to nine
simple `Initialize` (such as keys) into one composite `Initialize` by
placing them in tuples, and invoking the `apply` method.

The `<<=` method on `SettingKey[T]` is expecting an `Initialize[T]`, so you can use
this technique to create an `Initialize[T]` with multiple dependencies on
arbitrary keys.

Because function syntax in Scala just calls the `apply` method, you
could write the code like this, omitting the explicit `.apply` and just
treating `tuple` as a function:

```scala
val tuple: (Initialize[String], Initialize[String], Initialize[String]) = (name, organization, version)
val combined: Initialize[String] = tuple({ (n, o, v) =>
    "project " + n + " from " + o + " version " + v })
val setting: Setting[String] = name <<= combined
```

In a `build.sbt`, this code using intermediate `val` will not work, since you
can only write single expressions in a `.sbt` file, not multiple statements.

You can use a more concise syntax in `build.sbt`, like this:

```scala
name <<= (name, organization, version) { (n, o, v) => "project " + n + " from " + o + " version " + v }
```

Here the tuple of `Initialize` (also a tuple of `SettingKey`) works as a function,
taking the anonymous function delimited by `{}` as its argument, and returning an
`Initialize[T]` where `T` is the result type of the anonymous function.

Tuples of `Initialize` have one other method, `identity`, which simply
returns an `Initialize` with a tuple value.
`(a: Initialize[A], b: Initialize[B]).identity`
would result in a value of type
`Initialize[(A, B)]`. `identity` combines two `Initialize` into one, without
losing or modifying any of the values.

### When settings are undefined

Whenever a setting uses `~=` or `<<=` to create a dependency on itself or
another key's value, the value it depends on must exist. If it does not,
sbt will complain. It might say _"Reference to undefined setting"_, for
example. When this happens, be sure you're using the key in the
[[scope|Getting Started Scopes]] that defines it.

It's possible to create cycles, which is an error; sbt will tell you if you
do this.

### Tasks with dependencies

As noted in [[.sbt build definition|Getting Started Basic Def]], task keys create a
`Setting[Task[T]]` rather than a `Setting[T]` when you build a setting with
`:=`, `<<=`, etc. Similarly, task keys are instances of
`Initialize[Task[T]]` rather than `Initialize[T]`, and `<<=` on a task key
takes an `Initialize[Task[T]]` parameter.

The practical importance of this is that you can't have tasks as
dependencies for a non-task setting.

Take these two keys (from [Keys]):

```scala
val scalacOptions = TaskKey[Seq[String]]("scalac-options", "Options for the Scala compiler.")
val checksums = SettingKey[Seq[String]]("checksums", "The list of checksums to generate and to verify for dependencies.")
```

(`scalacOptions` and `checksums` have nothing to do with each other, they
are just two keys with the same value type, where one is a task.)

You cannot compile a `build.sbt` that tries to alias one of these to the
other like this:

```scala
scalacOptions <<= checksums

checksums <<= scalacOptions
```

The issue is that `scalacOptions.<<=` expects an
`Initialize[Task[Seq[String]]]` and `checksums.<<=` expects an
`Initialize[Seq[String]]`. There is, however, a way to convert an
`Initialize[T]` to an `Initialize[Task[T]]`, called `map`:

```scala
scalacOptions <<= checksums map identity
```

(`identity` is a standard Scala function that returns its input as its result.)

There is no way to go the _other_ direction, that is, a setting
key can't depend on a task key. That's because a setting key is
only computed once on project load, so the task would not be
re-run every time, and tasks expect to re-run every time.

A task can depend on both settings and other tasks, though, just use `map`
rather than `apply` to build an `Initialize[Task[T]]` rather than an `Initialize[T]`.
Remember the usage of `apply` with a non-task setting looks like this:

```scala
name <<= (name, organization, version) { (n, o, v) => "project " + n + " from " + o + " version " + v }
```

(`(name, organization, version)` has an apply method and is thus a function,
taking the anonymous function in `{}` braces as a parameter.)

To create an `Initialize[Task[T]]` you need a `map` in there rather than `apply`:

```scala
// this WON'T compile because name (on the left of <<=) is not a task and we used map
name <<= (name, organization, version) map { (n, o, v) => "project " + n + " from " + o + " version " + v }

// this WILL compile because packageBin is a task and we used map
packageBin in Compile <<= (name, organization, version) map { (n, o, v) => file(o + "-" + n + "-" + v + ".jar") }

// this WILL compile because name is not a task and we used apply
name <<= (name, organization, version) { (n, o, v) => "project " + n + " from " + o + " version " + v }

// this WON'T compile because packageBin is a task and we used apply
packageBin in Compile <<= (name, organization, version) { (n, o, v) => file(o + "-" + n + "-" + v + ".jar") }
```

_Bottom line:_ when converting a tuple of keys into an
`Initialize[Task[T]]`, use `map`; when converting a tuple of keys into an
`Initialize[T]` use `apply`; and you need the `Initialize[Task[T]]` if the
key on the left side of `<<=` is a `TaskKey[T]` rather than a
`SettingKey[T]`.

### Remember, aliases use `<<=` not `:=`

If you want one key to be an alias for another, you might be tempted to
use `:=` to create the following nonsense alias:

```scala
// doesn't work, and not useful
packageBin in Compile := packageDoc in Compile
```

The problem is that `:=`'s argument must be a value (or for tasks, a
function returning a value). For `packageBin` which
is a `TaskKey[File]`, it must be a `File` or a function `=> File`.
`packageDoc` is not a `File`, it's a key.

The proper way to do this is with `<<=`, which takes a key (really an
`Initialize`, but keys are instances of `Initialize`):

```scala
// works, still not useful
packageBin in Compile <<= packageDoc in Compile
```

Here, `<<=` expects an `Initialize[Task[File]]`, which is a computation that
will return a file later, when sbt runs the task. Which is what you want:
you want to alias a task by making it run another task, not by setting it
one time when sbt loads the project.

(By the way: the `in Compile` scope is needed to avoid "undefined" errors,
because the packaging tasks like `packageBin` are per-configuration, not
global.)


## Appending with dependencies: `<+=` and `<++=`

There are a couple more methods for appending to lists, which combine `+=`
and `++=` with `<<=`. That is, they let you compute a new list element or
new list to concatenate, using dependencies on other keys in order to do so.

These methods work exactly like `<<=`, but for `<++=`, the function you
write to convert the dependencies' values into a new value should create a
`Seq[T]` instead of a `T`.

Unlike `<<=` of course, `<+=` and `<++=` will append to the previous value
of the key on the left, rather than replacing it.

For example, say you have a coverage report named after the project, and you
want to add it to the files removed by `clean`:

```scala
cleanFiles <+= (name) { n => file("coverage-report-" + n + ".txt") }
```

## Next

At this point you know how to get things done with settings, so we can move
on to a specific key that comes up often: `libraryDependencies`.
[[Learn about library dependencies|Getting Started Library Dependencies]].
