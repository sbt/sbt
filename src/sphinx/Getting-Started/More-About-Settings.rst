=====================
More Kinds of Setting
=====================

This page explains other ways to create a ``Setting``, beyond the basic
``:=`` method. It assumes you've read :doc:`.sbt build definition <Basic-Def>` and :doc:`scopes <Scopes>`.

Refresher: Settings
-------------------

:doc:`Remember <Basic-Def>`, a build definition creates a
list of ``Setting``, which is then used to transform sbt's description
of the build (which is a map of key-value pairs). A ``Setting`` is a
transformation with sbt's earlier map as input and a new map as output.
The new map becomes sbt's new state.

Different settings transform the map in different ways.
:doc:`Earlier <Basic-Def>`, you read about the ``:=`` method.

The ``Setting`` which ``:=`` creates puts a fixed, constant value in the
new, transformed map. For example, if you transform a map with the
setting ``name := "hello"`` the new map has the string ``"hello"``
stored under the key ``name``.

Settings must end up in the master list of settings to do any good (all
lines in a ``build.sbt`` automatically end up in the list, but in a
:doc:`.scala file <Full-Def>` you can get it wrong by
creating a ``Setting`` without putting it where sbt will find it).

Appending to previous values: ``+=`` and ``++=``
------------------------------------------------

Assignment with ``:=`` is the simplest transformation, but keys have
other methods as well. If the ``T`` in ``SettingKey[T]`` is a sequence,
i.e. the key's value type is a sequence, you can append to the sequence
rather than replacing it.

-  ``+=`` will append a single element to the sequence.
-  ``++=`` will concatenate another sequence.

For example, the key ``sourceDirectories in Compile`` has a
``Seq[File]`` as its value. By default this key's value would include
``src/main/scala``. If you wanted to also compile source code in a
directory called ``source`` (since you just have to be nonstandard), you
could add that directory:

::

    sourceDirectories in Compile += new File("source")

Or, using the ``file()`` function from the sbt package for convenience:

::

    sourceDirectories in Compile += file("source")

(``file()`` just creates a new ``File``.)

You could use ``++=`` to add more than one directory at a time:

::

    sourceDirectories in Compile ++= Seq(file("sources1"), file("sources2"))

Where ``Seq(a, b, c, ...)`` is standard Scala syntax to construct a
sequence.

To replace the default source directories entirely, you use ``:=`` of
course:

::

    sourceDirectories in Compile := Seq(file("sources1"), file("sources2"))

Computing a value based on other keys' values
---------------------------------------------

Reference the value of another task or setting by calling ``value``
on the key for the task or setting.  The ``value`` method is special and may
only be called in the argument to ``:=``, ``+=``, or ``++=``.

As a first example, consider defining the project organization to be the same as the project name.

::

    // name our organization after our project (both are SettingKey[String])
    organization := name.value

Or, set the name to the name of the project's directory:

::

    // name is a Key[String], baseDirectory is a Key[File]
    // name the project after the directory it's inside
    name := baseDirectory.value.getName

This transforms the value of ``baseDirectory`` using the standard ``getName`` method of ``java.io.File``.

Using multiple inputs is similar.  For example,

::

    name := "project " + name.value + " from " + organization.value + " version " + version.value

This sets the name in terms of its previous value as well as the organization and version settings.

Settings with dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~

In the setting ``name := baseDirectory.value.getName``, ``name`` will have
a *dependency* on ``baseDirectory``. If you place the above in
``build.sbt`` and run the sbt interactive console, then type
``inspect name``, you should see (in part):

.. code-block:: text

    [info] Dependencies:
    [info]  *:baseDirectory

This is how sbt knows which settings depend on which other settings.
Remember that some settings describe tasks, so this approach also
creates dependencies between tasks.

For example, if you ``inspect compile`` you'll see it depends on another
key ``compileInputs``, and if you inspect ``compileInputs`` it in turn
depends on other keys. Keep following the dependency chains and magic
happens. When you type ``compile`` sbt automatically performs an
``update``, for example. It Just Works because the values required as
inputs to the ``compile`` computation require sbt to do the ``update``
computation first.

In this way, all build dependencies in sbt are *automatic* rather than
explicitly declared. If you use a key's value in another computation,
then the computation depends on that key. It just works!


When settings are undefined
~~~~~~~~~~~~~~~~~~~~~~~~~~~

Whenever a setting uses ``:=``, ``+=``, or ``++=`` to create a dependency on
itself or another key's value, the value it depends on must exist. If it
does not, sbt will complain. It might say *"Reference to undefined
setting"*, for example. When this happens, be sure you're using the key
in the :doc:`scope <Scopes>` that defines it.

It's possible to create cycles, which is an error; sbt will tell you if
you do this.

Tasks with dependencies
~~~~~~~~~~~~~~~~~~~~~~~

As noted in :doc:`.sbt build definition <Basic-Def>`, task
keys create a ``Setting[Task[T]]`` rather than a ``Setting[T]`` when you
build a setting with ``:=``, etc.  Tasks can use settings as inputs, but
settings cannot use tasks as inputs.

Take these two keys (from `Keys <../../sxr/Keys.scala.html>`_):

::

    val scalacOptions = taskKey[Seq[String]]("Options for the Scala compiler.")
    val checksums = settingKey[Seq[String]]("The list of checksums to generate and to verify for dependencies.")

(``scalacOptions`` and ``checksums`` have nothing to do with each other,
they are just two keys with the same value type, where one is a task.)

It is possible to compile a ``build.sbt`` that aliases ``scalacOptions`` to ``checksums``, but not the other way.
For example, this is allowed:

::

    // The scalacOptions task may be defined in terms of the checksums setting
    scalacOptions := checksums.value

There is no way to go the *other* direction.  That is, a setting key
can't depend on a task key. That's because a setting key is only
computed once on project load, so the task would not be re-run every
time, and tasks expect to re-run every time.

::

    // The checksums setting may not be defined in terms of the scalacOptions task
    checksums := scalacOptions.value


Appending with dependencies: ``+=`` and ``++=``
-------------------------------------------------

Other keys can be used when appending to an existing setting or task, just like they can for assigning with ``:=``.

For example, say you have a coverage report named after the project, and
you want to add it to the files removed by ``clean``:

::

    cleanFiles += file("coverage-report-" + name.value + ".txt")

Next
----

At this point you know how to get things done with settings, so we can
move on to a specific key that comes up often: ``libraryDependencies``.
:doc:`Learn about library dependencies <Library-Dependencies>`.
