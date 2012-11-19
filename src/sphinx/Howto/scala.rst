=========================
 Configure and use Scala
=========================

By default, sbt's interactive mode is started when no commands are provided on the command line or when the ``shell`` command is invoked.

.. howto::
   :id: version
   :title: Set the Scala version used for building the project
   :type: setting
   
   version := "1.0"

The ``scalaVersion`` configures the version of Scala used for compilation.  By default, sbt also adds a dependency on the Scala library with this version.  See the next section for how to disable this automatic dependency.  If the Scala version is not specified, the version sbt was built against is used.  It is recommended to explicitly specify the version of Scala.

For example, to set the Scala version to "2.9.2",

::

    scalaVersion := "2.9.2"

.. howto::
   :id: noauto
   :title: Disable the automatic dependency on the Scala library
   :type: setting
   
   autoScalaLibrary := false

sbt adds a dependency on the Scala standard library by default.  To disable this behavior, set the ``autoScalaLibrary`` setting to false.

::

    autoScalaLibrary := false

.. howto::
   :id: temporary
   :title: Temporarily switch to a different Scala version
   :type: command
   
   ++ 2.8.2

To set the Scala version in all scopes to a specific value, use the ``++`` command.  For example, to temporarily use Scala 2.8.2, run:

.. code-block:: console

    > ++ 2.8.2

.. howto::
   :id: local
   :title: Use a local Scala installation for building a project
   :type: setting
   
   scalaHome := Some(file("/path/to/scala/home/"))

Defining the ``scalaHome`` setting with the path to the Scala home directory will use that Scala installation.  sbt still requires ``scalaVersion`` to be set when a local Scala version is used.  For example,

::

    scalaVersion := "2.10.0-local"

    scalaHome := Some(file("/path/to/scala/home/"))

.. howto::
   :id: cross
   :title: Build a project against multiple Scala versions

See :doc:`cross building </Detailed-Topics/Cross-Build>`.

.. howto::
   :id: consoleQuick
   :title: Enter the Scala REPL with a project's dependencies on the classpath, but not the compiled project classes
   :type: command
   
   consoleQuick

The ``consoleQuick`` action retrieves dependencies and puts them on the classpath of the Scala REPL.  The project's sources are not compiled, but sources of any source dependencies are compiled.  To enter the REPL with test dependencies on the classpath but without compiling test sources, run ``test:consoleQuick``.  This will force compilation of main sources.

.. howto::
   :id: console
   :title: Enter the Scala REPL with a project's dependencies and compiled code on the classpath
   :type: command

   console

The ``console`` action retrieves dependencies and compiles sources and puts them on the classpath of the Scala REPL.  To enter the REPL with test dependencies and compiled test sources on the classpath, run ``test:console``.

.. howto::
   :id: consoleProject
   :title: Enter the Scala REPL with plugins and the build definition on the classpath
   :type: command
   
   consoleProject

.. code-block:: console

    > consoleProject

For details, see the :doc:`consoleProject </Detailed-Topics/Console-Project>` page.

.. howto::
   :id: initial
   :title: Define the initial commands evaluated when entering the Scala REPL
   :type: setting
   
   initialCommands in console := """println("Hi!")"""

Set ``initialCommands in console`` to set the initial statements to evaluate when ``console`` and ``consoleQuick`` are run.  To configure ``consoleQuick`` separately, use ``initialCommands in consoleQuick``.
For example,

::

    initialCommands in console := """println("Hello from console")"""

    initialCommands in consoleQuick := """println("Hello from consoleQuick")"""

The ``consoleProject`` command is configured separately by ``initialCommands in consoleProject``.  It does not use the value from ``initialCommands in console`` by default.  For example,

::

    initialCommands in consoleProject := """println("Hello from consoleProject")"""


.. howto::
   :id: embed
   :title: Use the Scala REPL from project code

sbt runs tests in the same JVM as sbt itself and Scala classes are not in the same class loader as the application classes.  This is also the case in ``console`` and when ``run`` is not forked. Therefore, when using the Scala interpreter, it is important to set it up properly to avoid an error message like:

.. code-block:: text

    Failed to initialize compiler: class scala.runtime.VolatileBooleanRef not found.
    ** Note that as of 2.8 scala does not assume use of the java classpath.
    ** For the old behavior pass -usejavacp to scala, or if using a Settings
    ** object programmatically, settings.usejavacp.value = true.

The key is to initialize the Settings for the interpreter using *embeddedDefaults*.  For example:

::
    
    val settings = new Settings
    settings.embeddedDefaults[MyType]
    val interpreter = new Interpreter(settings, ...)

Here, MyType is a representative class that should be included on the interpreter's classpath and in its application class loader.  For more background, see the `original proposal <https://gist.github.com/404272>`_ that resulted in *embeddedDefaults* being added.

Similarly, use a representative class as the type argument when using the *break* and *breakIf* methods of *ILoop*, as in the following example:

::
    
    def x(a: Int, b: Int) = {
      import scala.tools.nsc.interpreter.ILoop
      ILoop.breakIf[MyType](a != b, "a" -> a, "b" -> b )
    }
