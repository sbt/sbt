==============
Best Practices
==============

This page describes best practices for working with sbt. Nontrivial
additions and changes should generally be discussed on the `mailing
list <http://groups.google.com/group/simple-build-tool/topics>`_ first.
(Because there isn't built-in support for discussing GitHub wiki edits
like normal commits, a subpar suggestion can only be reverted in its
entirety without comment.)

``project/`` vs. ``~/.sbt/``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Anything that is necessary for building the project should go in
``project/``. This includes things like the web plugin. ``~/.sbt/``
should contain local customizations and commands for working with a
build, but are not necessary. An example is an IDE plugin.

Local settings
~~~~~~~~~~~~~~

There are two options for settings that are specific to a user. An
example of such a setting is inserting the local Maven repository at the
beginning of the resolvers list:

::

    resolvers <<= resolvers {rs =>
      val localMaven = "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"
      localMaven +: rs
    }

1. Put settings specific to a user in a global ``.sbt`` file, such as
   ``~/.sbt/local.sbt``. These settings will be applied to all projects.
2. Put settings in a ``.sbt`` file in a project that isn't checked into
   version control, such as ``<project>/local.sbt``. sbt combines the
   settings from multiple ``.sbt`` files, so you can still have the
   standard ``<project>/build.sbt`` and check that into version control.

.sbtrc
~~~~~~

Put commands to be executed when sbt starts up in a ``.sbtrc`` file, one
per line. These commands run before a project is loaded and are useful
for defining aliases, for example. sbt executes commands in
``$HOME/.sbtrc`` (if it exists) and then ``<project>/.sbtrc`` (if it
exists).

Generated files
~~~~~~~~~~~~~~~

Write any generated files to a subdirectory of the output directory,
which is specified by the ``target`` setting. This makes it easy to
clean up after a build and provides a single location to organize
generated files. Any generated files that are specific to a Scala
version should go in ``crossTarget`` for efficient cross-building.

For generating sources and resources, see :doc:`/Howto/generatefiles`.

Don't hard code
~~~~~~~~~~~~~~~

Don't hard code constants, like the output directory ``target/``. This
is especially important for plugins. A user might change the ``target``
setting to point to ``build/``, for example, and the plugin needs to
respect that. Instead, use the setting, like:

::

    myDirectory <<= target(_ / "sub-directory")

Don't "mutate" files
~~~~~~~~~~~~~~~~~~~~

A build naturally consists of a lot of file manipulation. How can we
reconcile this with the task system, which otherwise helps us avoid
mutable state? One approach, which is the recommended approach and the
approach used by sbt's default tasks, is to only write to any given file
once and only from a single task.

A build product (or by-product) should be written exactly once by only
one task. The task should then, at a minimum, provide the Files created
as its result. Another task that wants to use Files should map the task,
simultaneously obtaining the File reference and ensuring that the task
has run (and thus the file is constructed). Obviously you cannot do much
about the user or other processes modifying the files, but you can make
the I/O that is under the build's control more predictable by treating
file contents as immutable at the level of Tasks.

For example:

::

    lazy val makeFile = TaskKey[File]("make-file")

    // define a task that creates a file,
    //  writes some content, and returns the File
    // The write is completely 
    makeFile := {
        val f: File = file("/tmp/data.txt")
        IO.write(f, "Some content")
        f
    }

    // The result of makeFile is the constructed File,
    //   so useFile can map makeFile and simultaneously
    //   get the File and declare the dependency on makeFile
    useFile <<= makeFile map { (f: File) =>
        doSomething( f )
    }

This arrangement is not always possible, but it should be the rule and
not the exception.

Use absolute paths
~~~~~~~~~~~~~~~~~~

Construct only absolute Files. Either specify an absolute path

::

    file("/home/user/A.scala")

or construct the file from an absolute base:

::

    base / "A.scala"

This is related to the no hard coding best practice because the proper
way involves referencing the ``baseDirectory`` setting. For example, the
following defines the myPath setting to be the ``<base>/licenses/``
directory.

::

    myPath <<= baseDirectory(_ / "licenses")

In Java (and thus in Scala), a relative File is relative to the current
working directory. The working directory is not always the same as the
build root directory for a number of reasons.

The only exception to this rule is when specifying the base directory
for a Project. Here, sbt will resolve a relative File against the build
root directory for you for convenience.

Parser combinators
~~~~~~~~~~~~~~~~~~

1. Use ``token`` everywhere to clearly delimit tab completion
   boundaries.
2. Don't overlap or nest tokens. The behavior here is unspecified and
   will likely generate an error in the future.
3. Use ``flatMap`` for general recursion. sbt's combinators are strict
   to limit the number of classes generated, so use ``flatMap`` like:

   ``scala lazy val parser: Parser[Int] = token(IntBasic) flatMap { i =>    if(i <= 0)     success(i)   else     token(Space ~> parser) }``
   This example defines a parser a whitespace-delimited list of
   integers, ending with a negative number, and returning that final,
   negative number.


