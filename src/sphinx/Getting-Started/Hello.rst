============
Hello, World
============

This page assumes you've :doc:`installed sbt <Setup>`.

Create a project directory with source code
-------------------------------------------

A valid sbt project can be a directory containing a single source file.
Try creating a directory ``hello`` with a file ``hw.scala``, containing
the following:

::

    object Hi {
      def main(args: Array[String]) = println("Hi!")
    }

Now from inside the ``hello`` directory, start sbt and type ``run`` at
the sbt interactive console. On Linux or OS X the commands might look
like this:

::

      $ mkdir hello
      $ cd hello
      $ echo 'object Hi { def main(args: Array[String]) = println("Hi!") }' > hw.scala
      $ sbt
      ...
      > run
      ...
      Hi!

In this case, sbt works purely by convention. sbt will find the
following automatically:

-  Sources in the base directory
-  Sources in ``src/main/scala`` or ``src/main/java``
-  Tests in ``src/test/scala`` or ``src/test/java``
-  Data files in ``src/main/resources`` or ``src/test/resources``
-  jars in ``lib``

By default, sbt will build projects with the same version of Scala used
to run sbt itself.

You can run the project with ``sbt run`` or enter the `Scala
REPL <http://www.scala-lang.org/node/2097>`_ with ``sbt console``.
``sbt console`` sets up your project's classpath so you can try out live
Scala examples based on your project's code.

Build definition
----------------

Most projects will need some manual setup. Basic build settings go in a
file called ``build.sbt``, located in the project's base directory.

For example, if your project is in the directory ``hello``, in
``hello/build.sbt`` you might write:

::

    name := "hello"

    version := "1.0"

    scalaVersion := "2.9.1"

Notice the blank line between every item. This isn't just for show;
they're actually required in order to separate each item. In :doc:`.sbt build definition <Basic-Def>` you'll learn more about
how to write a ``build.sbt`` file.

If you plan to package your project in a jar, you will want to set at
least the name and version in a ``build.sbt``.

Setting the sbt version
-----------------------

You can force a particular version of sbt by creating a file
``hello/project/build.properties``. In this file, write:

::

    sbt.version=0.12.0

From 0.10 onwards, sbt is 99% source compatible from release to release.
Still, setting the sbt version in ``project/build.properties`` avoids
any potential confusion.

Next
====

Learn about the :doc:`file and directory layout <Directories>` of an sbt project.
