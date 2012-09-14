[Keys]: http://harrah.github.com/xsbt/latest/sxr/Keys.scala.html "Keys.scala"
[Defaults]: http://harrah.github.com/xsbt/latest/sxr/Defaults.scala.html "Defaults.scala"
[IO]: http://harrah.github.com/xsbt/latest/api/index.html#sbt.IO$ "IO object"

# Custom Settings and Tasks

[[Previous|Getting Started Multi-Project]] _Getting Started Guide page
13 of 14._ [[Next|Getting Started Summary]]

This page gets you started creating your own settings and tasks.

To understand this page, be sure you've read earlier pages in the
Getting Started Guide, especially
[[build.sbt|Getting Started Basic Def]] and
[[more about settings|Getting Started More About Settings]].

## Defining a key

[Keys] is packed with examples illustrating how to define
keys. Most of the keys are implemented in [Defaults].

Keys have one of three types. `SettingKey` and `TaskKey` are described in
[[.sbt build definition|Getting Started Basic Def]]. Read about `InputKey` on the [[Input Tasks]]
page.

Some examples from [Keys]:

```scala
val scalaVersion = SettingKey[String]("scala-version", "The version of Scala used for building.")
val clean = TaskKey[Unit]("clean", "Deletes files produced by the build, such as generated sources, compiled classes, and task caches.")
```

The key constructors have two string parameters: the name of the key
(`"scala-version"`) and a documentation string (`"The version of scala used for
building."`).

Remember from [[.sbt build definition|Getting Started Basic Def]] that the type parameter `T` in `SettingKey[T]`
indicates the type of value a setting has. `T` in `TaskKey[T]` indicates the
type of the task's result. Also remember from [[.sbt build definition|Getting Started Basic Def]]
that a setting has a fixed value until project reload, while a task is re-computed
for every "task execution" (every time someone types a command at the sbt
interactive prompt or in batch mode).

Keys may be defined in a `.scala` file (as described in
[[.scala build definition|Getting Started Full Def]]), or in a plugin (as described in
[[using plugins|Getting Started Using Plugins]]). Any `val` found in a `Build` object in your `.scala` build definition files, or any `val` found in a `Plugin` object from a plugin, will be imported automatically into your `.sbt` files.

## Implementing a task

Once you've defined a key, you'll need to use it in some task. You could be
defining your own task, or you could be planning to redefine an existing
task. Either way looks the same; if the task has no dependencies on other
settings or tasks, use `:=` to associate some code with the task key:

```scala
sampleStringTask := System.getProperty("user.home")

sampleIntTask := {
  val sum = 1 + 2
  println("sum: " + sum)
  sum
}
```

If the task has dependencies, you'd use `<<=` instead of course, as
discussed in [[more about settings|Getting Started More About Settings]].

The hardest part about implementing tasks is often not sbt-specific; tasks
are just Scala code. The hard part could be writing the "meat" of your task
that does whatever you're trying to do. For example, maybe you're trying to
format HTML in which case you might want to use an HTML library (you would
[[add a library dependency to your build definition|Getting Started Using Plugins]] and
write code based on the HTML library, perhaps).

sbt has some utility libraries and convenience functions, in particular you
can often use the convenient APIs in [IO] to manipulate files and directories.

## Extending but not replacing a task

If you want to run an existing task while also taking another action, use
`~=` or `<<=` to take the existing task as input (which will imply running
that task), and then do whatever else you like after the previous
implementation completes.

```scala
// These two settings are equivalent
intTask <<= intTask map { (value: Int) => value + 1 }
intTask ~= { (value: Int) => value + 1 }
```

## Use plugins!

If you find you have a lot of custom code in `.scala` files, consider moving
it to a plugin for re-use across multiple projects.

It's very easy to create a plugin, as [[teased earlier|Getting Started Using Plugins]] and
[[discussed at more length here|Plugins]].

## Next

This page has been a quick taste; there's much much more about custom tasks
on the [[Tasks]] page.

You're at the end of Getting Started! There's a [[brief recap|Getting Started Summary]].
