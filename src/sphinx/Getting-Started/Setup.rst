=====
Setup
=====

Overview
========

To create an sbt project, you'll need to take these steps:

-  Install sbt and create a script to launch it.
-  Setup a simple :doc:`hello world <Hello>` project

   -  Create a project directory with source files in it.
   -  Create your build definition.

-  Move on to :doc:`running <Running>` to learn how to run
   sbt.
-  Then move on to :doc:`.sbt build definition <Basic-Def>`
   to learn more about build definitions.

Installing sbt
==============

The latest development versions of 0.13.0 are available as nightly builds on |typesafe-snapshots|_.

To use a nightly build, the instructions are the same for normal manual setup except:

1. Download the launcher jar from one of the subdirectories of |nightly-launcher|.
   They should be listed in chronological order, so the most recent one will be last.
2. The version number is the name of the subdirectory and is of the form
   ``0.13.x-yyyyMMdd-HHmmss``. Use this in a ``build.properties`` file.
3. Call your script something like ``sbt-nightly`` to retain access to a
   stable ``sbt`` launcher.  The documentation will refer to the script as ``sbt``, however.

Related to the third point, remember that an ``sbt.version`` setting in
``<build-base>/project/build.properties`` determines the version of sbt
to use in a project. If it is not present, the default version
associated with the launcher is used. This means that you must set
``sbt.version=yyyyMMdd-HHmmss`` in an existing
``<build-base>/project/build.properties``. You can verify the right
version of sbt is being used to build a project by running
``sbtVersion``.

To reduce problems, it is recommended to not use a launcher jar for one
nightly version to launch a different nightly version of sbt.

Manual Installation
-------------------

.. _manual installation:

Windows
~~~~~~~

Create a batch file ``sbt.bat``:

.. code-block:: console

    $ set SCRIPT_DIR=%~dp0
    $ java -Xmx512M -jar "%SCRIPT_DIR%sbt-launch.jar" %*

and put sbt-launch.jar in the same directory as the batch file. Put ``sbt.bat`` on your path so
that you can launch ``sbt`` in any directory by typing ``sbt`` at the command prompt.

Unix
~~~~

Put sbt-launch.jar in ``~/bin``.

Create a script to run the jar, by placing this in a file called ``sbt``
in your ``~/bin`` directory:

.. code-block:: console

    java -Xms512M -Xmx1536M -Xss1M -XX:+CMSClassUnloadingEnabled -XX:MaxPermSize=384M -jar `dirname $0`/sbt-launch.jar "$@"

Make the script executable:

.. code-block:: console

    $ chmod u+x ~/bin/sbt

Tips and Notes
--------------

If you have any trouble running sbt, see :doc:`/Detailed-Topics/Setup-Notes` on terminal
encodings, HTTP proxies, and JVM options.

Next
----

Move on to :doc:`create a simple project <Hello>`.

