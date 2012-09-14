---
layout: howto
title: Configure and use Scala
sections:
 - id: version
   name: set the Scala version used for building the project
   setting: version := "1.0"
 - id: noauto
   name: disable the automatic dependency on the Scala library
   setting: autoScalaLibrary := false
 - id: temporary
   name: temporarily switch to a different Scala version
   command: ++ 2.8.2
 - id: local
   name: use a local Scala installation for building a project
   setting: scalaHome := Some(file("/path/to/scala/home/"))
 - id: cross
   name: build a project against multiple Scala versions
 - id: console-quick
   name: enter the Scala REPL with a project's dependencies on the classpath, but not the compiled project classes
   command: console-quick
 - id: console
   name: enter the Scala REPL with a project's dependencies and compiled code on the classpath
   command: console
 - id: console-project
   name: enter the Scala REPL with plugins and the build definition on the classpath
   command: console-project
 - id: initial
   name: define the initial commands evaluated when entering the Scala REPL
   setting: initialCommands in console := """println("Hi!")"""
 - id: embed
   name: use the Scala REPL from project code
---

[console-project]: https://github.com/harrah/xsbt/wiki/Console-Project
[cross building]: https://github.com/harrah/xsbt/wiki/Cross-Build
[original proposal]: https://gist.github.com/404272

By default, sbt's interactive mode is started when no commands are provided on the command line or when the `shell` command is invoked.

<h4 id="version">Set the Scala version used for building the project</h4>

The `scalaVersion` configures the version of Scala used for compilation.  By default, sbt also adds a dependency on the Scala library with this version.  See the next section for how to disable this automatic dependency.  If the Scala version is not specified, the version sbt was built against is used.  It is recommended to explicitly specify the version of Scala.

For example, to set the Scala version to "2.9.2",

{% highlight scala %}
scalaVersion := "2.9.2"
{% endhighlight %}

<h4 id="noauto">Disable the automatic dependency on the Scala library</h4>

sbt adds a dependency on the Scala standard library by default.  To disable this behavior, set the `autoScalaLibrary` setting to false.

{% highlight scala %}
autoScalaLibrary := false
{% endhighlight %}

<h4 id="temporary">Temporarily switch to a different Scala version</h4>

To set the Scala version in all scopes to a specific value, use the `++` command.  For example, to temporarily use Scala 2.8.2, run:

    > ++ 2.8.2

<h4 id="local">Use a local Scala version</h4>

Defining the `scalaHome` setting with the path to the Scala home directory will use that Scala installation.  sbt still requires `scalaVersion` to be set when a local Scala version is used.  For example,

{% highlight scala %}
scalaVersion := "2.10.0-local"

scalaHome := Some(file("/path/to/scala/home/"))
{% endhighlight %}

<h4 id="cross">Build a project against multiple Scala versions</h4>

See [cross building].

<h4 id="console-quick">Enter the Scala REPL with a project's dependencies on the classpath, but not the compiled project classes</h4>

The `console-quick` action retrieves dependencies and puts them on the classpath of the Scala REPL.  The project's sources are not compiled, but sources of any source dependencies are compiled.  To enter the REPL with test dependencies on the classpath but without compiling test sources, run `test:console-quick`.  This will force compilation of main sources.

<h4 id="console">Enter the Scala REPL with a project's dependencies and compiled classes on the classpath</h4>

The `console` action retrieves dependencies and compiles sources and puts them on the classpath of the Scala REPL.  To enter the REPL with test dependencies and compiled test sources on the classpath, run `test:console`.

<h4 id="console-project">Enter the Scala REPL with plugins and the build definition on the classpath</h4>

    > console-project

For details, see the [console-project] page.

<h4 id="initial">Define the initial commands evaluated when entering the Scala REPL</h4>

Set `initialCommands in console` to set the initial statements to evaluate when `console` and `console-quick` are run.  To configure `console-quick` separately, use `initialCommands in consoleQuick`.
For example,

{% highlight scala %}
initialCommands in console := """println("Hello from console")"""

initialCommands in consoleQuick := """println("Hello from console-quick")"""
{% endhighlight %}

The `console-project` command is configured separately by `initialCommands in consoleProject`.  It does not use the value from `initialCommands in console` by default.  For example,

{% highlight scala %}
initialCommands in consoleProject := """println("Hello from console-project")"""
{% endhighlight %}

<h4 id="embed">Use the Scala REPL from project code</h4>

sbt runs tests in the same JVM as sbt itself and Scala classes are not in the same class loader as the application classes.  This is also the case in `console` and when `run` is not forked. Therefore, when using the Scala interpreter, it is important to set it up properly to avoid an error message like:

    Failed to initialize compiler: class scala.runtime.VolatileBooleanRef not found.
    ** Note that as of 2.8 scala does not assume use of the java classpath.
    ** For the old behavior pass -usejavacp to scala, or if using a Settings
    ** object programmatically, settings.usejavacp.value = true.

The key is to initialize the Settings for the interpreter using _embeddedDefaults_.  For example:

{% highlight scala %}
val settings = new Settings
settings.embeddedDefaults[MyType]
val interpreter = new Interpreter(settings, ...)
{% endhighlight %}

Here, MyType is a representative class that should be included on the interpreter's classpath and in its application class loader.  For more background, see the [original proposal] that resulted in _embeddedDefaults_ being added.

Similarly, use a representative class as the type argument when using the _break_ and _breakIf_ methods of _ILoop_, as in the following example:

{% highlight scala %}
def x(a: Int, b: Int) = {
  import scala.tools.nsc.interpreter.ILoop
  ILoop.breakIf[MyType](a != b, "a" -> a, "b" -> b )
}
{% endhighlight %}