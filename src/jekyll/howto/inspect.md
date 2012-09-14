---
layout: howto
title: Inspect the build
sections:
 - id: taskhelp
   name: show or search help for a command, task, or setting
   command: help compile
 - id: listtasks
   name: list available tasks
   command: tasks
 - id: listsettings
   name: list available settings
   command: settings
 - id: dependencies
   name: display forward and reverse dependencies of a setting or task
   command: inspect compile
 - id: taskdependencytree
   name: display tree of setting/task dependencies
   command: inspect compile
 - id: description
   name: display the description and type of a setting or task
   command: help compile
 - id: delegates
   name: display the delegation chain of a setting or task
   command: inspect compile
 - id: related
   name: display related settings or tasks
   command: inspect compile
 - id: session
   name: show the current session (temporary) settings
   command: session list
 - id: projects
   name: show the list of projects and builds
   command: projects
 - id: about
   name: show basic information about sbt and the current build
   command: about
 - id: value
   name: show the value of a setting
   command: show name
 - id: result
   name: show the result of executing a task
   command: show update
 - id: classpath
   name: show the classpath used for compilation or testing
   command: show compile:dependency-classpath
 - id: applications
   name: show the main classes detected in a project
   command: show compile:discovered-main-classes
 - id: tests
   name: show the test classes detected in a project
   command: show defined-test-names
---

[Inspecting Settings]: https://github.com/harrah/xsbt/wiki/Inspecting-Settings

<h4 id="taskhelp">Show or search help for a command, task, or setting</h4>

The `help` command is used to show available commands and search the help for commands, tasks, or settings.
If run without arguments, `help` lists the available commands.

{% highlight console %}
> help

  help                         Displays this help message or prints detailed help on 
                                  requested commands (run 'help <command>').
  about                        Displays basic information about sbt and the build.
  reload                       (Re)loads the project in the current directory
  ...
{% endhighlight %}

{% highlight console %}
> help compile
{% endhighlight %}

If the argument passed to `help` is the name of an existing command, setting or task, the help
for that entity is displayed.  Otherwise, the argument is interpreted as a regular expression that
is used to search the help of all commands, settings and tasks.

The `tasks` command is like `help`, but operates only on tasks.
Similarly, the `settings` command only operates on settings.

See also `help help`, `help tasks`, and `help settings`.

<h4 id="listtasks">List available tasks</h4>

The `tasks` command, without arguments, lists the most commonly used tasks.
It can take a regular expression to search task names and descriptions.
The verbosity can be increased to show or search less commonly used tasks.
See `help tasks` for details.

<h4 id="listsettings">List available tasks</h4>

The `settings` command, without arguments, lists the most commonly used settings.
It can take a regular expression to search setting names and descriptions.
The verbosity can be increased to show or search less commonly used settings.
See `help settings` for details.

<h4 id="dependencies">Display forward and reverse dependencies of a setting or task</h4>

The `inspect` command displays several pieces of information about a given setting or task, including
the dependencies of a task/setting as well as the tasks/settings that depend on the it.  For example,

{% highlight console %}
> inspect test:compile
...
[info] Dependencies:
[info] 	test:compile::compile-inputs
[info] 	test:compile::streams
[info] Reverse dependencies:
[info] 	test:defined-test-names
[info] 	test:defined-sbt-plugins
[info] 	test:print-warnings
[info] 	test:discovered-main-classes
[info] 	test:defined-tests
[info] 	test:exported-products
[info] 	test:products
...
{% endhighlight %}

See the [Inspecting Settings] page for details.

<h4 id="taskdependencytree">Display tree of setting/task dependencies</h4>

In addition to displaying immediate forward and reverse dependencies as described in the previous section,
the `inspect` command can display the full dependency tree for a task or setting.
For example,

{% highlight console %}
> inspect tree clean
[info] *:clean = Task[Unit]
[info]   +-*:clean-files = List(<project>/lib_managed, <project>/target)
[info]   | +-{.}/*:managed-directory = lib_managed
[info]   | +-*:target = target
[info]   |   +-*:base-directory = <project>
[info]   |     +-*:this-project = Project(id: demo, base: <project>, ...
[info]   |     
[info]   +-*:clean-keep-files = List(<project>/target/.history)
[info]     +-*:history = Some(<project>/target/.history)
...
{% endhighlight %}

For each task, `inspect tree` show the type of the value generated by the task.
For a setting, the `toString` of the setting is displayed.
See the [Inspecting Settings] page for details on the `inspect` command.

<h4 id="description">Display the description and type of a setting or task</h4>

While the `help`, `settings`, and `tasks` commands display a description of a task,
the `inspect` command also shows the type of a setting or task and the value of a setting.
For example:

{% highlight console %}
> inspect update
[info] Task: sbt.UpdateReport
[info] Description:
[info] 	Resolves and optionally retrieves dependencies, producing a report.
...
{% endhighlight %}

{% highlight console %}
> inspect scala-version
[info] Setting: java.lang.String = 2.9.2
[info] Description:
[info] 	The version of Scala used for building.
...
{% endhighlight %}

See the [Inspecting Settings] page for details.

<h4 id="delegates">Display the delegation chain of a setting or task</h4>

See the [Inspecting Settings] page for details.

<h4 id="related">Display related settings or tasks</h4>

The `inspect` command can help find scopes where a setting or task is defined.
The following example shows that different options may be specified to the Scala
for testing and API documentation generation.

{% highlight console %}
> inspect scalac-options
...
[info] Related:
[info] 	compile:doc::scalac-options
[info] 	test:scalac-options
[info] 	*/*:scalac-options
[info] 	test:doc::scalac-options
{% endhighlight %}

See the [Inspecting Settings] page for details.

<h4 id="projects">Show the list of projects and builds</h4>

The `projects` command displays the currently loaded projects.
The projects are grouped by their enclosing build and the current project is indicated by an asterisk.
For example,

{% highlight console %}
> projects
[info] In file:/home/user/demo/
[info] 	 * parent
[info] 	   sub
[info] In file:/home/user/dep/
[info] 	   sample
{% endhighlight %}

<h4 id="session">Show the current session (temporary) settings</h4>

`session list` displays the settings that have been added at the command line for the current project.  For example,

{% highlight console %}
> session list
  1. maxErrors := 5
  2. scalacOptions += "-explaintypes"
{% endhighlight %}

`session list-all` displays the settings added for all projects.
For details, see `help session`.

<h4 id="about">Show basic information about sbt and the current build</h4>

{% highlight console %}
> about
[info] This is sbt 0.12.0
[info] The current project is {file:~/code/sbt.github.com/}default
[info] The current project is built against Scala 2.9.2
[info] Available Plugins: com.jsuereth.ghpages.GhPages, com.jsuereth.git.GitPlugin, com.jsuereth.sbtsite.SitePlugin
[info] sbt, sbt plugins, and build definitions are using Scala 2.9.2
{% endhighlight %}

<h4 id="value">Show the value of a setting</h4>

The `inspect` shows the value of a setting as part of its output, but the `show` command is dedicated to this job.
It shows the output of the setting provided as an argument.
For example,

{% highlight console %}
> show organization
[info] com.github.sbt 
{% endhighlight %}

The `show` command also works for tasks, described next.

<h4 id="result">Show the result of executing a task</h4>

{% highlight console %}
> show update
... <output of update> ...
[info] Update report:
[info] 	Resolve time: 122 ms, Download time: 5 ms, Download size: 0 bytes
[info] 	compile:
[info] 		org.scala-lang:scala-library:2.9.2: ...
{% endhighlight %}

The `show` command will execute the task provided as an argument and then print the result.
Note that this is different from the behavior of the `inspect` command (described in other sections),
which does not execute a task and thus can only display its type and not its generated value.

<h4 id="compilecp">Show the classpath used for compilation or testing</h4>

{% highlight console %}
> show compile:dependency-classpath
...
[info] ArrayBuffer(Attributed(~/.sbt/0.12.0/boot/scala-2.9.2/lib/scala-library.jar))
{% endhighlight %}

For the test classpath,

{% highlight console %}
> show test:dependency-classpath
...
[info] ArrayBuffer(Attributed(~/code/sbt.github.com/target/scala-2.9.2/classes), Attributed(~/.sbt/0.12.0/boot/scala-2.9.2/lib/scala-library.jar), Attributed(~/.ivy2/cache/junit/junit/jars/junit-4.8.2.jar))
{% endhighlight %}

<h4 id="applications">Show the main classes detected in a project</h4>

sbt detects the classes with public, static main methods for use by the `run` method and to tab-complete the `run-main` method.
The `discovered-main-classes` task does this discovery and provides as its result the list of class names.
For example, the following shows the main classes discovered in the main sources:

{% highlight console %}
> show compile:discovered-main-classes
... <runs compile if out of date> ...
[info] List(org.example.Main)
{% endhighlight %}

<h4 id="tests">Show the test classes detected in a project</h4>

sbt detects tests according to fingerprints provided by test frameworks.
The `defined-test-names` task provides as its result the list of test names detected in this way.
For example,

{% highlight console %}
> show test:defined-test-names
... < runs test:compile if out of date > ...
[info] List(org.example.TestA, org.example.TestB)
{% endhighlight %}
