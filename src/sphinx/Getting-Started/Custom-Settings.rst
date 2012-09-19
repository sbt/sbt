
=========================
Custom Settings and Tasks
=========================

This page gets you started creating your own settings and tasks.

To understand this page, be sure you've read earlier pages in the
Getting Started Guide, especially :doc:`build.sbt <Basic-Def>` and :doc:`more about settings <More-About-Settings>`.

Defining a key
--------------

`Keys <../../sxr/Keys.scala.html>`_ is
packed with examples illustrating how to define keys. Most of the keys
are implemented in
`Defaults <../../sxr/Defaults.scala.html>`_.

Keys have one of three types. ``SettingKey`` and ``TaskKey`` are
described in :doc:`.sbt build definition <Basic-Def>`. Read
about ``InputKey`` on the :doc:`/Extending/Input-Tasks` page.

Some examples from `Keys <../../sxr/Keys.scala.html>`_:

::

    val scalaVersion = SettingKey[String]("scala-version", "The version of Scala used for building.")
    val clean = TaskKey[Unit]("clean", "Deletes files produced by the build, such as generated sources, compiled classes, and task caches.")

The key constructors have two string parameters: the name of the key
(``"scala-version"``) and a documentation string
(``"The version of scala used for building."``).

Remember from :doc:`.sbt build definition <Basic-Def>` that
the type parameter ``T`` in ``SettingKey[T]`` indicates the type of
value a setting has. ``T`` in ``TaskKey[T]`` indicates the type of the
task's result. Also remember from :doc:`.sbt build definition <Basic-Def>`
that a setting has a fixed value until project
reload, while a task is re-computed for every "task execution" (every
time someone types a command at the sbt interactive prompt or in batch
mode).

Keys may be defined in a ``.scala`` file (as described in :doc:`.scala build definition <Full-Def>`),
or in a plugin (as described in
:doc:`using plugins <Using-Plugins>`). Any ``val`` found in
a ``Build`` object in your ``.scala`` build definition files, or any
``val`` found in a ``Plugin`` object from a plugin, will be imported
automatically into your ``.sbt`` files.

Implementing a task
-------------------

Once you've defined a key, you'll need to use it in some task. You could
be defining your own task, or you could be planning to redefine an
existing task. Either way looks the same; if the task has no
dependencies on other settings or tasks, use ``:=`` to associate some
code with the task key:

::

    sampleStringTask := System.getProperty("user.home")

    sampleIntTask := {
      val sum = 1 + 2
      println("sum: " + sum)
      sum
    }

If the task has dependencies, you'd use ``<<=`` instead of course, as
discussed in :doc:`more about settings <More-About-Settings>`.

The hardest part about implementing tasks is often not sbt-specific;
tasks are just Scala code. The hard part could be writing the "meat" of
your task that does whatever you're trying to do. For example, maybe
you're trying to format HTML in which case you might want to use an HTML
library (you would :doc:`add a library dependency to your build definition <Using-Plugins>`
and write code based on the HTML library, perhaps).

sbt has some utility libraries and convenience functions, in particular
you can often use the convenient APIs in
`IO <../../api/index.html#sbt.IO$>`_ to
manipulate files and directories.

Extending but not replacing a task
----------------------------------

If you want to run an existing task while also taking another action,
use ``~=`` or ``<<=`` to take the existing task as input (which will
imply running that task), and then do whatever else you like after the
previous implementation completes.

::

    // These two settings are equivalent
    intTask <<= intTask map { (value: Int) => value + 1 }
    intTask ~= { (value: Int) => value + 1 }

Use plugins!
------------

If you find you have a lot of custom code in ``.scala`` files, consider
moving it to a plugin for re-use across multiple projects.

It's very easy to create a plugin, as :doc:`teased earlier <Using-Plugins>` and :doc:`discussed at more length here </Extending/Plugins>`.

Next
----

This page has been a quick taste; there's much much more about custom
tasks on the :doc:`/Detailed-Topics/Tasks` page.

You're at the end of Getting Started! There's a :doc:`brief recap <Summary>`.
