=========================
Configure and use logging
=========================

.. howto::
   :id: last
   :title: View the logging output of the previously executed command
   :type: command

   last

When a command is run, more detailed logging output is sent to a file than to the screen (by default).
This output can be recalled for the command just executed by running `last`.

For example, the output of :key:`run` when the sources are uptodate is:

.. code-block:: console

    > run
    [info] Running A
    Hi!
    [success] Total time: 0 s, completed Feb 25, 2012 1:00:00 PM


The details of this execution can be recalled by running `last`:

.. code-block:: console

    > last
    [debug] Running task... Cancelable: false, max worker threads: 4, check cycles: false
    [debug]
    [debug] Initial source changes:
    [debug] 	removed:Set()
    [debug] 	added: Set()
    [debug] 	modified: Set()
    [debug] Removed products: Set()
    [debug] Modified external sources: Set()
    [debug] Modified binary dependencies: Set()
    [debug] Initial directly invalidated sources: Set()
    [debug]
    [debug] Sources indirectly invalidated by:
    [debug] 	product: Set()
    [debug] 	binary dep: Set()
    [debug] 	external source: Set()
    [debug] Initially invalidated: Set()
    [debug] Copy resource mappings:
    [debug]
    [info] Running A
    [debug] Starting sandboxed run...
    [debug] Waiting for threads to exit or System.exit to be called.
    [debug]   Classpath:
    [debug] 	/tmp/e/target/scala-2.9.2/classes
    [debug] 	/tmp/e/.sbt/0.12.0/boot/scala-2.9.2/lib/scala-library.jar
    [debug] Waiting for thread runMain to exit
    [debug] 	Thread runMain exited.
    [debug] Interrupting remaining threads (should be all daemons).
    [debug] Sandboxed run complete..
    [debug] Exited with code 0
    [success] Total time: 0 s, completed Jan 1, 2012 1:00:00 PM

Configuration of the logging level for the console and for the backing file are described in following sections.

.. howto::
   :id: tasklast
   :title: View the previous logging output of a specific task
   :type: command

   last compile

When a task is run, more detailed logging output is sent to a file than to the screen (by default).
This output can be recalled for a specific task by running `last <task>`.
For example, the first time :key:`compile` is run, output might look like:

.. code-block:: console

    > compile
    [info] Updating {file:/.../demo/}example...
    [info] Resolving org.scala-lang#scala-library;2.9.2 ...
    [info] Done updating.
    [info] Compiling 1 Scala source to .../demo/target/scala-2.9.2/classes...
    [success] Total time: 0 s, completed Jun 1, 2012 1:11:11 PM

The output indicates that both dependency resolution and compilation were performed.
The detailed output of each of these may be recalled individually.
For example,

.. code-block:: console

    > last compile
    [debug]
    [debug] Initial source changes:
    [debug] 	removed:Set()
    [debug] 	added: Set(/home/mark/tmp/a/b/A.scala)
    [debug] 	modified: Set()
    ...

and:

.. code-block:: console

    > last update
    [info] Updating {file:/.../demo/}example...
    [debug] post 1.3 ivy file: using exact as default matcher
    [debug] :: resolving dependencies :: example#example_2.9.2;0.1-SNAPSHOT
    [debug] 	confs: [compile, runtime, test, provided, optional, compile-internal, runtime-internal, test-internal, plugin, sources, docs, pom]
    [debug] 	validate = true
    [debug] 	refresh = false
    [debug] resolving dependencies for configuration 'compile'
    ...

.. howto::
   :id: printwarnings
   :title: Show warnings from the previous compilation
   :type: command

   printWarnings

The Scala compiler does not print the full details of warnings by default.
Compiling code that uses the deprecated `error` method from Predef might generate the following output:

.. code-block:: console

    > compile
    [info] Compiling 1 Scala source to <...>/classes...
    [warn] there were 1 deprecation warnings; re-run with -deprecation for details
    [warn] one warning found

The details aren't provided, so it is necessary to add `-deprecation` to the options passed to the compiler (:key:`scalacOptions`) and recompile.
An alternative when using Scala 2.10 and later is to run :key:`printWarnings`.
This task will display all warnings from the previous compilation.
For example,

.. code-block:: console

    > printWarnings
    [warn] A.scala:2: method error in object Predef is deprecated: Use sys.error(message) instead
    [warn] 	def x = error("Failed.")
    [warn] 	        ^

.. howto::
   :id: level
   :title: Change the logging level globally
   :type: command

   warn

The quickest way to change logging levels is by using the `error`, `warn`, `info`, or `debug` commands.
These set the default logging level for commands and tasks.
For example,

.. code-block:: console

    > warn

will by default show only warnings and errors.
To set the logging level before any commands are executed on startup, use `--` before the logging level.  For example,

.. code-block:: console

    $ sbt --warn
    > compile
    [warn] there were 2 feature warning(s); re-run with -feature for details
    [warn] one warning found
    [success] Total time: 4 s, completed ...
    >

The logging level can be overridden at a finer granularity, which is described next.

.. howto::
  :id: tasklevel
  :title: Change the logging level for a specific task, configuration, or project
  :type: setting

  logLevel in compile := Level.Debug

The amount of logging is controlled by the :key:`logLevel` setting, which takes values from the `Level` enumeration.
Valid values are `Error`, `Warn`, `Info`, and `Debug` in order of increasing verbosity.
The logging level may be configured globally, as described in the previous section, or it may be applied to a specific project, configuration, or task.
For example, to change the logging level for compilation to only show warnings and errors:

.. code-block:: console

    > set logLevel in compile := Level.Warn

To enable debug logging for all tasks in the current project,

.. code-block:: console

    > set logLevel := Level.Warn

A common scenario is that after running a task, you notice that you need more information than was shown by default.
A :key:`logLevel` based solution typically requires changing the logging level and running a task again.
However, there are two cases where this is unnecessary.
First, warnings from a previous compilation may be displayed using :key:`printWarnings` for the main sources or `test:printWarnings` for test sources.
Second, output from the previous execution is available either for a single task or for in its entirety.
See the section on `printWarnings <#printwarnings>`_ and the sections on `previous output <#last>`_.


.. howto::
   :id: trace
   :title: Configure printing of stack traces
   :type: command

   set every traceLevel := 5`

By default, sbt hides the stack trace of most exceptions thrown during execution.
It prints a message that indicates how to display the exception.
However, you may want to show more of stack traces by default.

The setting to configure is :key:`traceLevel`, which is a setting with an Int value.
When :key:`traceLevel` is set to a negative value, no stack traces are shown.
When it is zero, the stack trace is displayed up to the first sbt stack frame.
When positive, the stack trace is shown up to that many stack frames.

For example, the following configures sbt to show stack traces up to the first sbt frame:

.. code-block:: console

    > set every traceLevel := 0

The `every` part means to override the setting in all scopes.
To change the trace printing behavior for a single project, configuration, or task, scope :key:`traceLevel` appropriately:

.. code-block:: console

    > set traceLevel in Test := 5
    > set traceLevel in update := 0
    > set traceLevel in ThisProject := -1

.. howto::
   :id: nobuffer
   :title: Print the output of tests immediately instead of buffering
   :type: setting

   logBuffered := false

By default, sbt buffers the logging output of a test until the whole class finishes.
This is so that output does not get mixed up when executing in parallel.
To disable buffering, set the :key:`logBuffered` setting to false:

::

    logBuffered := false

.. howto::
   :id: custom
   :title: Add a custom logger

The setting :key:`extraLoggers` can be used to add custom loggers.
A custom logger should implement [AbstractLogger].
:key:`extraLoggers` is a function `ScopedKey[_] => Seq[AbstractLogger]`.
This means that it can provide different logging based on the task that requests the logger.

::

    extraLoggers := {
      val currentFunction = extraLoggers.value
    	(key: ScopedKey[_]) => {
    		myCustomLogger(key) +: currentFunction(key)
    	}
    }

Here, we take the current function `currentFunction` for the setting and provide a new function.
The new function prepends our custom logger to the ones provided by the old function.

.. howto::
   :id: log
   :title: Log messages in a task

The special task :key:`streams` provides per-task logging and I/O via a `Streams <../../api/#sbt.std.Streams>`_ instance.
To log, a task uses the `log` member from the :key:`streams` task:

::

    myTask := {
	   val log = streams.value.log
    	log.warn("A warning.")
    }
