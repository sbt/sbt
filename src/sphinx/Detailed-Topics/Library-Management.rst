==================
Library Management
==================

There's now a :doc:`getting started page </Getting-Started/Library-Dependencies>`
about library management, which you may want to read first.

*Wiki Maintenance Note:* it would be nice to remove the overlap between
this page and the getting started page, leaving this page with the more
advanced topics such as checksums and external Ivy files.

Introduction
============

There are two ways for you to manage libraries with sbt: manually or
automatically. These two ways can be mixed as well. This page discusses
the two approaches. All configurations shown here are settings that go
either directly in a :doc:`.sbt file </Getting-Started/Basic-Def>` or are
appended to the ``settings`` of a Project in a :doc:`.scala file </Getting-Started/Full-Def>`.

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

    unmanagedBase <<= baseDirectory { base => base / "custom_lib" }

If you want more control and flexibility, override the
``unmanaged-jars`` task, which ultimately provides the manual
dependencies to sbt. The default implementation is roughly:

::

    unmanagedJars in Compile <<= baseDirectory map { base => (base ** "*.jar").classpath }

If you want to add jars from multiple directories in addition to the
default directory, you can do:

::

    unmanagedJars in Compile <++= baseDirectory map { base =>
        val baseDirectories = (base / "libA") +++ (base / "b" / "lib") +++ (base / "libC")
        val customJars = (baseDirectories ** "*.jar") +++ (base / "d" / "my.jar")
        customJars.classpath
    }

See :doc:`Paths` for more information on building up paths.

Automatic Dependency Management
===============================

This method of dependency management involves specifying the direct
dependencies of your project and letting sbt handle retrieving and
updating your dependencies. sbt supports three ways of specifying these
dependencies:

-  Declarations in your project definition
-  Maven POM files (dependency definitions only: no repositories)
-  Ivy configuration and settings files

sbt uses `Apache Ivy <http://ant.apache.org/ivy/>`_ to implement
dependency management in all three cases. The default is to use inline
declarations, but external configuration can be explicitly selected. The
following sections describe how to use each method of automatic
dependency management.

Inline Declarations
-------------------

Inline declarations are a basic way of specifying the dependencies to be
automatically retrieved. They are intended as a lightweight alternative
to a full configuration using Ivy.

Dependencies
~~~~~~~~~~~~

Declaring a dependency looks like:

::

    libraryDependencies += groupID % artifactID % revision

or

::

    libraryDependencies += groupID % artifactID % revision % configuration

See :doc:`/Dormant/Configurations` for details on configuration mappings. Also,
several dependencies can be declared together:

::

    libraryDependencies ++= Seq(
        groupID %% artifactID % revision,
        groupID %% otherID % otherRevision
    )

If you are using a dependency that was built with sbt, double the first
``%`` to be ``%%``:

::

    libraryDependencies += groupID %% artifactID % revision

This will use the right jar for the dependency built with the version of
Scala that you are currently using. If you get an error while resolving
this kind of dependency, that dependency probably wasn't published for
the version of Scala you are using. See :doc:`Cross-Build` for details.

Ivy can select the latest revision of a module according to constraints
you specify. Instead of a fixed revision like ``"1.6.1"``, you specify
``"latest.integration"``, ``"2.9.+"``, or ``"[1.0,)"``. See the `Ivy
revisions <http://ant.apache.org/ivy/history/2.2.0/ivyfile/dependency.html#revision>`_
documentation for details.

Resolvers
~~~~~~~~~

sbt uses the standard Maven2 repository by default.

Declare additional repositories with the form:

::

    resolvers += name at location

For example:

::

    libraryDependencies ++= Seq(
        "org.apache.derby" % "derby" % "10.4.1.3",
        "org.specs" % "specs" % "1.6.1"
    )

    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

sbt can search your local Maven repository if you add it as a
repository:

::

    resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

See :doc:`Resolvers` for details on defining other types of repositories.

Override default resolvers
~~~~~~~~~~~~~~~~~~~~~~~~~~

``resolvers`` configures additional, inline user resolvers. By default,
``sbt`` combines these resolvers with default repositories (Maven
Central and the local Ivy repository) to form ``external-resolvers``. To
have more control over repositories, set ``external-resolvers``
directly. To only specify repositories in addition to the usual
defaults, configure ``resolvers``.

For example, to use the Sonatype OSS Snapshots repository in addition to
the default repositories,

::

    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

To use the local repository, but not the Maven Central repository:

::

    externalResolvers <<= resolvers map { rs =>
      Resolver.withDefaultResolvers(rs, mavenCentral = false)
    }

Override all resolvers for all builds
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

The repositories used to retrieve sbt, Scala, plugins, and application
dependencies can be configured globally and declared to override the
resolvers configured in a build or plugin definition. There are two
parts:

1. Define the repositories used by the launcher.
2. Specify that these repositories should override those in build
   definitions.

The repositories used by the launcher can be overridden by defining
``~/.sbt/repositories``, which must contain a ``[repositories]`` section
with the same format as the :doc:`Launcher` configuration file. For
example:

::

    [repositories]
    local
    my-maven-repo: http://example.org/repo
    my-ivy-repo: http://example.org/ivy-repo/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext]

A different location for the repositories file may be specified by the
``sbt.repository.config`` system property in the sbt startup script. The
final step is to set ``sbt.override.build.repos`` to true to use these
repositories for dependency resolution and retrieval.

Explicit URL
~~~~~~~~~~~~

If your project requires a dependency that is not present in a
repository, a direct URL to its jar can be specified as follows:

::

    libraryDependencies += "slinky" % "slinky" % "2.1" from "http://slinky2.googlecode.com/svn/artifacts/2.1/slinky.jar"

The URL is only used as a fallback if the dependency cannot be found
through the configured repositories. Also, the explicit URL is not
included in published metadata (that is, the pom or ivy.xml).

Disable Transitivity
~~~~~~~~~~~~~~~~~~~~

By default, these declarations fetch all project dependencies,
transitively. In some instances, you may find that the dependencies
listed for a project aren't necessary for it to build. Projects using
the Felix OSGI framework, for instance, only explicitly require its main
jar to compile and run. Avoid fetching artifact dependencies with either
``intransitive()`` or ``notTransitive()``, as in this example:

::

    libraryDependencies += "org.apache.felix" % "org.apache.felix.framework" % "1.8.0" intransitive()

Classifiers
~~~~~~~~~~~

You can specify the classifier for a dependency using the ``classifier``
method. For example, to get the jdk15 version of TestNG:

::

    libraryDependencies += "org.testng" % "testng" % "5.7" classifier "jdk15"

For multiple classifiers, use multiple ``classifier`` calls:

::

    libraryDependencies += 
      "org.lwjgl.lwjgl" % "lwjgl-platform" % lwjglVersion classifier "natives-windows" classifier "natives-linux" classifier "natives-osx"

To obtain particular classifiers for all dependencies transitively, run
the ``update-classifiers`` task. By default, this resolves all artifacts
with the ``sources`` or ``javadoc`` classifier. Select the classifiers
to obtain by configuring the ``transitive-classifiers`` setting. For
example, to only retrieve sources:

::

    transitiveClassifiers := Seq("sources")

Exclude Transitive Dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

To exclude certain transitive dependencies of a dependency, use the
``excludeAll`` or ``exclude`` methods. The ``exclude`` method should be
used when a pom will be published for the project. It requires the
organization and module name to exclude. For example,

::

    libraryDependencies += 
      "log4j" % "log4j" % "1.2.15" exclude("javax.jms", "jms")

The ``excludeAll`` method is more flexible, but because it cannot be
represented in a pom.xml, it should only be used when a pom doesn't need
to be generated. For example,

::

    libraryDependencies +=
      "log4j" % "log4j" % "1.2.15" excludeAll(
        ExclusionRule(organization = "com.sun.jdmk"),
        ExclusionRule(organization = "com.sun.jmx"),
        ExclusionRule(organization = "javax.jms")
      )

See
`ModuleID <../../api/sbt/ModuleID.html>`_
for API details.

Download Sources
~~~~~~~~~~~~~~~~

Downloading source and API documentation jars is usually handled by an
IDE plugin. These plugins use the ``update-classifiers`` and
``update-sbt-classifiers`` tasks, which produce an :doc:`Update-Report`
referencing these jars.

To have sbt download the dependency's sources without using an IDE
plugin, add ``withSources()`` to the dependency definition. For API
jars, add ``withJavadoc()``. For example:

::

    libraryDependencies += 
      "org.apache.felix" % "org.apache.felix.framework" % "1.8.0" withSources() withJavadoc()

Note that this is not transitive. Use the ``update-*classifiers`` tasks
for that.

Extra Attributes
~~~~~~~~~~~~~~~~

`Extra
attributes <http://ant.apache.org/ivy/history/2.2.0/concept.html#extra>`_
can be specified by passing key/value pairs to the ``extra`` method.

To select dependencies by extra attributes:

::

    libraryDependencies += "org" % "name" % "rev" extra("color" -> "blue")

To define extra attributes on the current project:

::

    projectID <<= projectID { id =>
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
:doc:`Setup </Getting-Started/Setup>`).

For example:

::

    java -Dsbt.ivy.home=/tmp/.ivy2/ ...

Checksums
~~~~~~~~~

sbt (`through
Ivy <http://ant.apache.org/ivy/history/latest-milestone/concept.html#checksum>`_)
verifies the checksums of downloaded files by default. It also publishes
checksums of artifacts by default. The checksums to use are specified by
the *checksums* setting.

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

Finally, see :doc:`Publishing` for how to publish your project.

.. _external-maven-ivy:

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

or

::

    externalIvySettings(url("your_url_here"))

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

