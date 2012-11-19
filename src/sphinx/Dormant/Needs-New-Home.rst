*Wiki Maintenance Note:* This page is a dumping ground for little bits
of text, examples, and information that needs to find a new home
somewhere else on the wiki.

Snippets of docs that need to move to another page
==================================================

Temporarily change the logging level and configure how stack traces are
displayed by modifying the ``log-level`` or ``trace-level`` settings:

.. code-block:: console

    > set logLevel := Level.Warn

Valid ``Level`` values are ``Debug, Info, Warn, Error``.

You can run an action for multiple versions of Scala by prefixing the
action with ``+``. See [[Cross Build]] for details. You can temporarily
switch to another version of Scala using ``++ <version>``. This version
does not have to be listed in your build definition, but it does have to
be available in a repository. You can also include the initial command
to run after switching to that version. For example:

.. code-block:: console

    > ++2.9.1 console-quick
    ...
    Welcome to Scala version 2.9.1.final (Java HotSpot(TM) Server VM, Java 1.6.0).
    ...
    scala>
    ...
    > ++2.8.1 console-quick
    ...
    Welcome to Scala version 2.8.1 (Java HotSpot(TM) Server VM, Java 1.6.0).
    ...
    scala>

Manual Dependency Management
============================

Manually managing dependencies involves copying any jars that you want
to use to the ``lib`` directory. sbt will put these jars on the
classpath during compilation, testing, running, and when using the
interpreter. You are responsible for adding, removing, updating, and
otherwise managing the jars in this directory. No modifications to your
project definition are required to use this method unless you would like
to change the location of the directory you store the jars in.

To change the directory jars are stored in, change the
``unmanaged-base`` setting in your project definition. For example, to
use ``custom_lib/``:

::

    unmanagedBase := baseDirectory.value / "custom_lib"

If you want more control and flexibility, override the
``unmanaged-jars`` task, which ultimately provides the manual
dependencies to sbt. The default implementation is roughly:

::

    unmanagedJars in Compile := (baseDirectory.value ** "*.jar").classpath

If you want to add jars from multiple directories in addition to the
default directory, you can do:

::

    unmanagedJars in Compile ++= {
        val base = baseDirectory.value
        val baseDirectories = (base / "libA") +++ (base / "b" / "lib") +++ (base / "libC")
        val customJars = (baseDirectories ** "*.jar") +++ (base / "d" / "my.jar")
        customJars.classpath
    }

See [[Paths]] for more information on building up paths.

Resolver.withDefaultResolvers method
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To use the local and Maven Central repositories, but not the Scala Tools
releases repository:

::

    externalResolvers :=
      Resolver.withDefaultResolvers(resolvers.value, mavenCentral = true, scalaTools = false)

Explicit URL
~~~~~~~~~~~~

If your project requires a dependency that is not present in a
repository, a direct URL to its jar can be specified with the ``from``
method as follows:

::

    libraryDependencies += "slinky" % "slinky" % "2.1" from "http://slinky2.googlecode.com/svn/artifacts/2.1/slinky.jar"

The URL is only used as a fallback if the dependency cannot be found
through the configured repositories. Also, when you publish a project, a
pom or ivy.xml is created listing your dependencies; the explicit URL is
not included in this published metadata.

Disable Transitivity
~~~~~~~~~~~~~~~~~~~~

By default, sbt fetches all dependencies, transitively. (That is, it
downloads the dependencies of the dependencies you list.)

In some instances, you may find that the dependencies listed for a
project aren't necessary for it to build. Avoid fetching artifact
dependencies with ``intransitive()``, as in this example:

::

    libraryDependencies += "org.apache.felix" % "org.apache.felix.framework" % "1.8.0" intransitive()

Classifiers
~~~~~~~~~~~

You can specify the classifer for a dependency using the ``classifier``
method. For example, to get the jdk15 version of TestNG:

::

    libraryDependencies += "org.testng" % "testng" % "5.7" classifier "jdk15"

To obtain particular classifiers for all dependencies transitively, run
the ``update-classifiers`` task. By default, this resolves all artifacts
with the ``sources`` or ``javadoc`` classifer. Select the classifiers to
obtain by configuring the ``transitive-classifiers`` setting. For
example, to only retrieve sources:

::

    transitiveClassifiers := Seq("sources")

Extra Attributes
~~~~~~~~~~~~~~~~

[Extra attributes] can be specified by passing key/value pairs to the
``extra`` method.

To select dependencies by extra attributes:

::

    libraryDependencies += "org" % "name" % "rev" extra("color" -> "blue")

To define extra attributes on the current project:

::

    projectID ~= { id =>
        id extra("color" -> "blue", "component" -> "compiler-interface")
    }

Inline Ivy XML
~~~~~~~~~~~~~~

sbt additionally supports directly specifying the configurations or
dependencies sections of an Ivy configuration file inline. You can mix
this with inline Scala dependency and repository declarations.

For example:

::

    ivyXML :=
      <dependencies>
        <dependency org="javax.mail" name="mail" rev="1.4.2">
          <exclude module="activation"/>
        </dependency>
      </dependencies>

Ivy Home Directory
~~~~~~~~~~~~~~~~~~

By default, sbt uses the standard Ivy home directory location
``${user.home}/.ivy2/``. This can be configured machine-wide, for use by
both the sbt launcher and by projects, by setting the system property
``sbt.ivy.home`` in the sbt startup script (described in
[[Setup\|Getting Started Setup]]).

For example:

.. code-block:: console

    java -Dsbt.ivy.home=/tmp/.ivy2/ ...

Checksums
~~~~~~~~~

sbt ([through Ivy]) verifies the checksums of downloaded files by
default. It also publishes checksums of artifacts by default. The
checksums to use are specified by the *checksums* setting.

To disable checksum checking during update:

::

    checksums in update := Nil

To disable checksum creation during artifact publishing:

::

    checksums in publishLocal := Nil

    checksums in publish := Nil

The default value is:

::

    checksums := Seq("sha1", "md5")

Publishing
~~~~~~~~~~

Finally, see [[Publishing]] for how to publish your project.

Maven/Ivy
---------

For this method, create the configuration files as you would for Maven
(``pom.xml``) or Ivy (``ivy.xml`` and optionally ``ivysettings.xml``).
External configuration is selected by using one of the following
expressions.

Ivy settings (resolver configuration)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

::

    externalIvySettings()

or

::

    externalIvySettings(baseDirectory(_ / "custom-settings-name.xml"))

Ivy file (dependency configuration)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

::

    externalIvyFile()

or

::

    externalIvyFile(baseDirectory(_ / "custom-name.xml"))

Because Ivy files specify their own configurations, sbt needs to know
which configurations to use for the compile, runtime, and test
classpaths. For example, to specify that the Compile classpath should
use the 'default' configuration:

::

    classpathConfiguration in Compile := config("default")

Maven pom (dependencies only)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

::

    externalPom()

or

::

    externalPom(baseDirectory(_ / "custom-name.xml"))

Full Ivy Example
~~~~~~~~~~~~~~~~

For example, a ``build.sbt`` using external Ivy files might look like:

::

    externalIvySettings()

    externalIvyFile( baseDirectory { base => base / "ivyA.xml"} )

    classpathConfiguration in Compile := Compile

    classpathConfiguration in Test := Test

    classpathConfiguration in Runtime := Runtime

Known limitations
~~~~~~~~~~~~~~~~~

Maven support is dependent on Ivy's support for Maven POMs. Known issues
with this support:

-  Specifying ``relativePath`` in the ``parent`` section of a POM will
   produce an error.
-  Ivy ignores repositories specified in the POM. A workaround is to
   specify repositories inline or in an Ivy ``ivysettings.xml`` file.

Configuration dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~

The GSG on multi-project builds doesn't describe delegation among
configurations. The FAQ entry about porting multi-project build from 0.7
mentions "configuration dependencies" but there's nothing really to link
to that explains them.

These should be FAQs (maybe just pointing to topic pages)
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  Run your program in its own VM
-  Run your program with a particular version of Scala
-  Run your webapp within an embedded jetty server
-  Create a WAR that can be deployed to an external app server

