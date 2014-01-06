=====================================
Getting Started with the Sbt Launcher
=====================================

The sbt launcher component is a self-contained jar that boots a Scala
application or server without Scala or the application already existing
on the system. The only prerequisites are the launcher jar itself, an 
optional configuration file, and a java runtime version 1.6 or greater.

Overview
========

A user downloads the launcher jar and creates a script to run it. In
this documentation, the script will be assumed to be called `launch`.
For unix, the script would look like:
`java -jar sbt-launcher.jar "$@"`

The user can now launch servers and applications which provide sbt
launcher configuration.

Applications
------------

To launch an application, the user then downloads the configuration 
file for the application (call it `my.app.configuration`) and creates 
a script to launch it (call it `myapp`): `launch @my.app.configuration "$@"`

The user can then launch the application using `myapp arg1 arg2 ...`

More on launcher configuration can be found at :doc:`Launcher Configuration </Launcher/Configuration>`


Servers
-------

The sbt launcher can be used to launch and discover running servers
on the system.   The launcher can be used to launch servers similarly to
applications.  However, if desired, the launcher can also be used to
ensure that only one instance of a server is running at time.  This is done
by having clients always use the launcher as a *service locator*.

To discover where a server is running (or launch it if it is not running), 
the user downloads the configuration file for the server
(call it `my.server.configuration`) and creates a script to discover 
the server (call it `find-myserver`): `launch --locate @my.server.properties`.

This command will print out one string, the URI at which to reach the server,
e.g. `sbt://127.0.0.1:65501`.  Clients should use the IP/port to connect to
to the server and initiate their connection.

When using the `locate` feature, the sbt launcher makes these following
restrictions to servers:

- The Server must have a starting class that extends 
  the `xsbti.ServerMain` class
- The Server must have an entry point (URI) that clients
  can use to detect the server
- The server must have defined a lock file which the launcher can
  use to ensure that only one instance is running at a time
- The filesystem on which the lock file resides must support
  locking.
- The server must allow the launcher to open a socket against the port
  without sending any data.  This is used to check if a previous
  server is still alive.


Resolving Applications/Servers
------------------------------

Like the launcher used to distribute `sbt`, the downloaded launcher
jar will retrieve Scala and the application according to the provided
configuration file. The versions may be fixed or read from a different
configuration file (the location of which is also configurable). The
location to which the Scala and application jars are downloaded is
configurable as well. The repositories searched are configurable.
Optional initialization of a properties file on launch is configurable.

Once the launcher has downloaded the necessary jars, it loads the
application/server and calls its entry point. The application is passed
information about how it was called: command line arguments, current
working directory, Scala version, and application ID (organization,
name, version). In addition, the application can ask the launcher to
perform operations such as obtaining the Scala jars and a
`ClassLoader` for any version of Scala retrievable from the
repositories specified in the configuration file. It can request that
other applications be downloaded and run. When the application
completes, it can tell the launcher to exit with a specific exit code or
to reload the application with a different version of Scala, a different
version of the application, or different arguments.

There are some other options for setup, such as putting the
configuration file inside the launcher jar and distributing that as a
single download. The rest of this documentation describes the details of
configuring, writing, distributing, and running the application.


Creating a Launched Application
-------------------------------

This section shows how to make an application that is launched by this
launcher. First, declare a dependency on the launcher-interface. Do not
declare a dependency on the launcher itself. The launcher interface
consists strictly of Java interfaces in order to avoid binary
incompatibility between the version of Scala used to compile the
launcher and the version used to compile your application. The launcher
interface class will be provided by the launcher, so it is only a
compile-time dependency. If you are building with sbt, your dependency
definition would be:

.. parsed-literal::

      libraryDependencies += "org.scala-sbt" % "launcher-interface" % "|release|" % "provided"

      resolvers += sbtResolver.value

Make the entry point to your class implement 'xsbti.AppMain'. An example
that uses some of the information:

.. code-block:: scala

    package xsbt.test
    class Main extends xsbti.AppMain
    {
        def run(configuration: xsbti.AppConfiguration) =
        {
            // get the version of Scala used to launch the application
            val scalaVersion = configuration.provider.scalaProvider.version

            // Print a message and the arguments to the application
            println("Hello world!  Running Scala " + scalaVersion)
            configuration.arguments.foreach(println)

            // demonstrate the ability to reboot the application into different versions of Scala
            // and how to return the code to exit with
            scalaVersion match
            {
                case "2.9.3" =>
                    new xsbti.Reboot {
                        def arguments = configuration.arguments
                        def baseDirectory = configuration.baseDirectory
                        def scalaVersion = "2.10.2
                        def app = configuration.provider.id
                    }
                case "2.10.2" => new Exit(1)
                case _ => new Exit(0)
            }
        }
        class Exit(val code: Int) extends xsbti.Exit
    }

Next, define a configuration file for the launcher. For the above class,
it might look like:

.. parsed-literal::

    [scala]
      version: |scalaRelease|
    [app]
      org: org.scala-sbt
      name: xsbt-test
      version: |release|
      class: xsbt.test.Main
      cross-versioned: binary
    [repositories]
      local
      maven-central
    [boot]
      directory: ${user.home}/.myapp/boot

Then, `publishLocal` or `+publishLocal` the application to make it
available.  For more information, please see :doc:`Launcher Configuration </Detailed-Topics/Launcher/Configuration>`

Running an Application
----------------------

As mentioned above, there are a few options to actually run the
application. The first involves providing a modified jar for download.
The second two require providing a configuration file for download.

-  Replace the /sbt/sbt.boot.properties file in the launcher jar and
   distribute the modified jar. The user would need a script to run
   `java -jar your-launcher.jar arg1 arg2 ...`.
-  The user downloads the launcher jar and you provide the configuration
   file.

   -  The user needs to run `java -Dsbt.boot.properties=your.boot.properties -jar launcher.jar`.
   -  The user already has a script to run the launcher (call it
      'launch'). The user needs to run `launch @your.boot.properties your-arg-1 your-arg-2`


Execution
---------

Let's review what's happening when the launcher starts your application.

On startup, the launcher searches for its configuration and then 
parses it.  Once the final configuration is resolved, the launcher 
proceeds to obtain the necessary jars to launch the application. The
`boot.directory` property is used as a base directory to retrieve jars
to. Locking is done on the directory, so it can be shared system-wide.
The launcher retrieves the requested version of Scala to

.. code-block:: console

    ${boot.directory}/${scala.version}/lib/

If this directory already exists, the launcher takes a shortcut for
startup performance and assumes that the jars have already been
downloaded. If the directory does not exist, the launcher uses Apache
Ivy to resolve and retrieve the jars. A similar process occurs for the
application itself. It and its dependencies are retrieved to

.. code-block:: console

    ${boot.directory}/${scala.version}/${app.org}/${app.name}/.

Once all required code is downloaded, the class loaders are set up. The
launcher creates a class loader for the requested version of Scala. It
then creates a child class loader containing the jars for the requested
'app.components' and with the paths specified in `app.resources`. An
application that does not use components will have all of its jars in
this class loader.

The main class for the application is then instantiated. It must be a
public class with a public no-argument constructor and must conform to
xsbti.AppMain. The `run` method is invoked and execution passes to the
application. The argument to the 'run' method provides configuration
information and a callback to obtain a class loader for any version of
Scala that can be obtained from a repository in [repositories]. The
return value of the run method determines what is done after the
application executes. It can specify that the launcher should restart
the application or that it should exit with the provided exit code.
