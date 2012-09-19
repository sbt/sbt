==================
External Processes
==================

Usage
=====

``sbt`` includes a process library to simplify working with external
processes. The library is available without import in build definitions
and at the interpreter started by the :doc:`console-project <Console-Project>` task.

To run an external command, follow it with an exclamation mark ``!``:

::

    "find project -name *.jar" !

An implicit converts the ``String`` to ``sbt.ProcessBuilder``, which
defines the ``!`` method. This method runs the constructed command,
waits until the command completes, and returns the exit code.
Alternatively, the ``run`` method defined on ``ProcessBuilder`` runs the
command and returns an instance of ``sbt.Process``, which can be used to
``destroy`` the process before it completes. With no arguments, the
``!`` method sends output to standard output and standard error. You can
pass a ``Logger`` to the ``!`` method to send output to the ``Logger``:

::

    "find project -name *.jar" ! log

Two alternative implicit conversions are from ``scala.xml.Elem`` or
``List[String]`` to ``sbt.ProcessBuilder``. These are useful for
constructing commands. An example of the first variant from the android
plugin:

::

      <x> {dxPath.absolutePath} --dex --output={classesDexPath.absolutePath} {classesMinJarPath.absolutePath}</x> !

If you need to set the working directory or modify the environment, call
``sbt.Process`` explicitly, passing the command sequence (command and
argument list) or command string first and the working directory second.
Any environment variables can be passed as a vararg list of key/value
String pairs.

::

     Process("ls" :: "-l" :: Nil, Path.userHome, "key1" -> value1, "key2" -> value2) ! log

Operators are defined to combine commands. These operators start with
``#`` in order to keep the precedence the same and to separate them from
the operators defined elsewhere in ``sbt`` for filters. In the following
operator definitions, ``a`` and ``b`` are subcommands.

-  ``a #&& b`` Execute ``a``. If the exit code is nonzero, return that
   exit code and do not execute ``b``. If the exit code is zero, execute
   ``b`` and return its exit code.
-  ``a #|| b`` Execute ``a``. If the exit code is zero, return zero for
   the exit code and do not execute ``b``. If the exit code is nonzero,
   execute ``b`` and return its exit code.
-  ``a #| b`` Execute ``a`` and ``b``, piping the output of ``a`` to the
   input of ``b``.

There are also operators defined for redirecting output to ``File``\ s
and input from ``File``\ s and ``URL``\ s. In the following definitions,
``url`` is an instance of ``URL`` and ``file`` is an instance of
``File``.

-  ``a #< url`` or ``url #> a`` Use ``url`` as the input to ``a``. ``a``
   may be a ``File`` or a command.
-  ``a #< file`` or ``file #> a`` Use ``file`` as the input to ``a``.
   ``a`` may be a ``File`` or a command.
-  ``a #> file`` or ``file #< a`` Write the output of ``a`` to ``file``.
   ``a`` may be a ``File``, ``URL``, or a command.
-  ``a #>> file`` or ``file #<< a`` Append the output of ``a`` to
   ``file``. ``a`` may be a ``File``, ``URL``, or a command.

There are some additional methods to get the output from a forked
process into a ``String`` or the output lines as a ``Stream[String]``.
Here are some examples, but see the `ProcessBuilder
API <../../api/sbt/ProcessBuilder.html>`_
for details.

::

    val listed: String = "ls" !!
    val lines2: Stream[String] = "ls" lines_!

Finally, there is a ``cat`` method to send the contents of ``File``\ s
and ``URL``\ s to standard output.

Examples
--------

Download a ``URL`` to a ``File``:

::

    url("http://databinder.net/dispatch/About") #> file("About.html") !
    or
    file("About.html") #< url("http://databinder.net/dispatch/About") !

Copy a ``File``:

::

    file("About.html") #> file("About_copy.html") !
    or
    file("About_copy.html") #< file("About.html") !

Append the contents of a ``URL`` to a ``File`` after filtering through
``grep``:

::

    url("http://databinder.net/dispatch/About") #> "grep JSON" #>> file("About_JSON") !
    or
    file("About_JSON") #<< ( "grep JSON" #< url("http://databinder.net/dispatch/About") )  !

Search for uses of ``null`` in the source directory:

::

    "find src -name *.scala -exec grep null {} ;"  #|  "xargs test -z"  #&&  "echo null-free"  #||  "echo null detected"  !

Use ``cat``::

    val spde = url("http://technically.us/spde/About")
    val dispatch = url("http://databinder.net/dispatch/About")
    val build = file("project/build.properties")
    cat(spde, dispatch, build) #| "grep -i scala" !
