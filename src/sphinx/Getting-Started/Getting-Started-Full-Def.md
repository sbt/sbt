# `.scala` Build Definition

[[Previous|Getting Started Library Dependencies]] _Getting Started Guide page
10 of 14._ [[Next|Getting Started Using Plugins]]

This page assumes you've read previous pages in the Getting
Started Guide, _especially_
[[.sbt build definition|Getting Started Basic Def]] and
[[more about settings|Getting Started More About Settings]].

## sbt is recursive

`build.sbt` is so simple, it conceals how sbt really works. sbt builds are
defined with Scala code. That code, itself, has to be built. What better way
than with sbt?

The `project` directory _is another project inside your project_ which knows
how to build your project. The project inside `project` can (in theory) do
anything any other project can do. _Your build definition is an sbt
project._

And the turtles go all the way down. If you like, you can tweak the build
definition of the build definition project, by creating a `project/project/`
directory.

Here's an illustration.

```text

   hello/                  # your project's base directory

       Hello.scala         # a source file in your project (could be in
                           #   src/main/scala too)

       build.sbt           # build.sbt is part of the source code for the
                           #   build definition project inside project/

       project/            # base directory of the build definition project

           Build.scala     # a source file in the project/ project,
                           #   that is, a source file in the build definition

           build.sbt       # this is part of a build definition for a project
                           #   in project/project ; build definition's build
                           #   definition


           project/        # base directory of the build definition project
                           #   for the build definition

               Build.scala # source file in the project/project/ project
```

_Don't worry!_ Most of the time you are not going to need all that. But
understanding the principle can be helpful.

By the way: any time files ending in `.scala` or `.sbt` are used, naming them
`build.sbt` and `Build.scala` are conventions only. This also means that
multiple files are allowed.

## `.scala` source files in the build definition project

`.sbt` files are merged into their sibling `project`
directory. Looking back at the project layout:

```text

   hello/                  # your project's base directory

       build.sbt           # build.sbt is part of the source code for the
                           #   build definition project inside project/

       project/            # base directory of the build definition project

           Build.scala     # a source file in the project/ project,
                           #   that is, a source file in the build definition
```

The Scala expressions in `build.sbt` are compiled alongside and merged with
`Build.scala` (or any other `.scala` files in the `project/` directory).

_`.sbt` files in the base directory for a project become part of the
`project` build definition project also located in that base directory._

The `.sbt` file format is a convenient shorthand for adding
settings to the build definition project.

## Relating `build.sbt` to `Build.scala`

To mix `.sbt` and `.scala` files in your build definition, you need to
understand how they relate.

The following two files illustrate. First, if your project is in `hello`,
create `hello/project/Build.scala` as follows:

```scala
import sbt._
import Keys._

object HelloBuild extends Build {

    val sampleKeyA = SettingKey[String]("sample-a", "demo key A")
    val sampleKeyB = SettingKey[String]("sample-b", "demo key B")
    val sampleKeyC = SettingKey[String]("sample-c", "demo key C")
    val sampleKeyD = SettingKey[String]("sample-d", "demo key D")

    override lazy val settings = super.settings ++
        Seq(sampleKeyA := "A: in Build.settings in Build.scala", resolvers := Seq())

    lazy val root = Project(id = "hello",
                            base = file("."),
                            settings = Project.defaultSettings ++ Seq(sampleKeyB := "B: in the root project settings in Build.scala"))
}

```

Now, create `hello/build.sbt` as follows:

```scala
sampleKeyC in ThisBuild := "C: in build.sbt scoped to ThisBuild"

sampleKeyD := "D: in build.sbt"
```

Start up the sbt interactive prompt. Type `inspect sample-a` and you should
see (among other things):

```text
[info] Setting: java.lang.String = A: in Build.settings in Build.scala
[info] Provided by:
[info] 	{file:/home/hp/checkout/hello/}/*:sample-a
```

and then `inspect sample-c` and you should see:

```text
[info] Setting: java.lang.String = C: in build.sbt scoped to ThisBuild
[info] Provided by:
[info] 	{file:/home/hp/checkout/hello/}/*:sample-c
```

Note that the "Provided by" shows the same scope for the two values. That
is, `sampleKeyC in ThisBuild` in a `.sbt` file is equivalent to placing a
setting in the `Build.settings` list in a `.scala` file. sbt takes
build-scoped settings from both places to create the build definition.

Now, `inspect sample-b`:

```text
[info] Setting: java.lang.String = B: in the root project settings in Build.scala
[info] Provided by:
[info] 	{file:/home/hp/checkout/hello/}hello/*:sample-b
```

Note that `sample-b` is scoped to the project
(`{file:/home/hp/checkout/hello/}hello`) rather than the entire build
(`{file:/home/hp/checkout/hello/}`).

As you've probably guessed, `inspect sample-d` matches `sample-b`:

```text
[info] Setting: java.lang.String = D: in build.sbt
[info] Provided by:
[info] 	{file:/home/hp/checkout/hello/}hello/*:sample-d
```

sbt _appends_ the settings from `.sbt` files to the settings from
`Build.settings` and `Project.settings` which means `.sbt` settings take
precedence. Try changing `Build.scala` so it sets key `sample-c` or
`sample-d`, which are also set in `build.sbt`. The setting in `build.sbt`
should "win" over the one in `Build.scala`.

One other thing you may have noticed: `sampleKeyC` and `sampleKeyD` were
available inside `build.sbt`. That's because sbt imports the contents of
your `Build` object into your `.sbt` files. In this case `import
HelloBuild._` was implicitly done for the `build.sbt` file.

In summary:

 - In `.scala` files, you can add settings to `Build.settings` for sbt to
   find, and they are automatically build-scoped.
 - In `.scala` files, you can add settings to `Project.settings` for sbt to
   find, and they are automatically project-scoped.
 - Any `Build` object you write in a `.scala` file will have its contents
   imported and available to `.sbt` files.
 - The settings in `.sbt` files are _appended_ to the settings in `.scala`
   files.
 - The settings in `.sbt` files are project-scoped unless you explicitly
   specify another scope.

## When to use `.scala` files

In `.scala` files, you are not limited to a series of settings
expressions. You can write any Scala code including `val`, `object`, and
method definitions.

_One recommended approach is to define settings in `.sbt` files, using
`.scala` files when you need to factor out a `val` or `object` or method
definition._

Because the `.sbt` format allows only single expressions, it doesn't give
you a way to share code among expressions. When you need to share code, you
need a `.scala` file so you can set common variables or define methods.

There's one build definition, which is a nested project inside
your main project.  `.sbt` and `.scala` files are compiled
together to create that single definition.

`.scala` files are also required to define multiple projects in a single
build. More on that is coming up in
[[Multi-Project Builds|Getting Started Multi-Project]].

(A disadvantage of using `.sbt` files in a
[[multi-project build|Getting Started Multi-Project]] is that
they'll be spread around in different directories; for that
reason, some people prefer to put settings in their `.scala` files
if they have sub-projects. This will be clearer after you see
how [[multi-project builds|Getting Started Multi-Project]] work.)

## The build definition project in interactive mode

You can switch the sbt interactive prompt to have the build definition
project in `project/` as the current project. To do so, type `reload
plugins`.

```text
> reload plugins
[info] Set current project to default-a0e8e4 (in build file:/home/hp/checkout/hello/project/)
> show sources
[info] ArrayBuffer(/home/hp/checkout/hello/project/Build.scala)
> reload return
[info] Loading project definition from /home/hp/checkout/hello/project
[info] Set current project to hello (in build file:/home/hp/checkout/hello/)
> show sources
[info] ArrayBuffer(/home/hp/checkout/hello/hw.scala)
>
```

As shown above, you use `reload return` to leave the build definition
project and return to your regular project.

## Reminder: it's all immutable

It would be wrong to think that the settings in `build.sbt` are added to the
`settings` fields in `Build` and `Project` objects. Instead, the settings
list from `Build` and `Project`, and the settings from `build.sbt`, are
concatenated into another immutable list which is then used by sbt.  The
`Build` and `Project` objects are "immutable configuration" forming only
part of the complete build definition.

In fact, there are other sources of settings as well. They are appended in
this order:

 - Settings from `Build.settings` and `Project.settings` in your `.scala` files.
 - Your user-global settings; for example in `~/.sbt/build.sbt` you can
   define settings affecting _all_ your projects.
 - Settings injected by plugins, see [[using plugins|Getting Started Using Plugins]] coming up next.
 - Settings from `.sbt` files in the project.
 - Build definition projects (i.e. projects inside `project`) have
   settings from global plugins (`~/.sbt/plugins`) added.
   [[Using plugins|Getting Started Using Plugins]] explains this
   more.

Later settings override earlier ones. The entire list of settings forms the
build definition.

## Next

Move on to [[using plugins|Getting Started Using Plugins]].
