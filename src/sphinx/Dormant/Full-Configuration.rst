*Wiki Maintenance Note:* This page has been *mostly* replaced by
[[Getting Started Full Def]] and other pages. It has some obsolete
terminology:

-  we now avoid referring to build definition as "configuration" to
   avoid confusion with compile configurations
-  we now avoid referring to basic/light/quick vs. full configuration,
   in favor of ".sbt build definition files" and ".scala build
   definition files"

However, it may still be worth combing this page for examples or points
that are not made in new pages. Some stuff that may not be elsewhere:

-  discussion of cycles
-  discussion of build-level settings
-  discussion of omitting or augmenting defaults

Also, the discussion of configuration delegation which is teased here,
needs to exist somewhere.

After extracting useful content, this page could simply be a redirect
(delete the content, link to the new pages about build definition).

There is a related page [[Introduction to Full Configurations]] which
could benefit from cleanup at the same time.

Full Configuration (Draft)
==========================

A full configuration consists of one or more Scala source files that
define concrete Builds. A Build defines project relationships and
configurations.

By Example
----------

Create a file with extension ``.scala`` in your ``project/`` directory
(such as ``<your-project>/project/Build.scala``).

A sample ``project/Build.scala``:

::

    import sbt._

    object MyBuild extends Build {
      // Declare a project in the root directory of the build with ID "root".
      // Declare an execution dependency on sub1.
      lazy val root = Project("root", file(".")) aggregate(sub1)

      // Declare a project with ID 'sub1' in directory 'a'.
      // Declare a classpath dependency on sub2 in the 'test' configuration.
      lazy val sub1: Project = Project("sub1", file("a")) dependsOn(sub2 % "test")

      // Declare a project with ID 'sub2' in directory 'b'.
      // Declare a configuration dependency on the root project.
      lazy val sub2 = Project("sub2", file("b"), delegates = root :: Nil)
    }

Cycles
~~~~~~

(It is probably best to skip this section and come back after reading
about project relationships. It is near the example for easier
reference.)

The configuration dependency ``sub2 -> root`` is specified as an
argument to the ``delegates`` parameter of ``Project``, which is by-name
and of type ``Seq[ProjectReference]`` because by-name repeated
parameters are not allowed in Scala. There are also corresponding
by-name parameters ``aggregate`` and ``dependencies`` for execution and
classpath dependencies. By-name parameters, being non-strict, are useful
when there are cycles between the projects, as is the case for ``root``
and ``sub2``. In the example, there is a *configuration* dependency
``sub2 -> root``, a *classpath* dependency ``sub1 -> sub2``, and an
*execution* dependency ``root -> sub1``. This causes cycles at the
Scala-level, but not within a particular dependency type, which is not
allowed.

Defining Projects
-----------------

An internal project is defined by constructing an instance of
``Project``. The minimum information for a new project is its ID string
and base directory. For example:

\`\`\`scala import sbt.\_

object MyBuild extends Build { lazy val projectA = Project("a",
file("subA")) }
\`\`\ ``This constructs a project definition for a project with ID 'a' and located in the``\ /subA\ ``directory. Here,``\ file(...)\ ``is equivalent to``\ new
File(...)\` and is resolved relative to the build's base directory.
There are additional optional parameters to the Project constructor.
These parameters configure the project and declare project
relationships, as discussed in the next sections.

Project Settings
----------------

A full build definition can configure settings for a project, just like
a light configuration. Unlike a light configuration, the default
settings can be replaced or manipulated and sequences of settings can be
manipulated. In addition, a light configuration has default imports
defined. A full definition needs to import these explicitly. In
particular, all keys (like ``name`` and ``version``) need to be imported
from ``sbt.Keys``.

No defaults
~~~~~~~~~~~

For example, to define a build from scratch (with no default settings or
tasks):

::

    import sbt._
    import Keys._

    object MyBuild extends Build {
      lazy val projectA = Project("a", file("subA"), settings = Seq(name := "From Scratch"))
    }

Augment Defaults
~~~~~~~~~~~~~~~~

To augment the default settings, the following Project definitions are
equivalent:

::

    lazy val a1 = Project("a", file("subA")) settings(name := "Additional", version := "1.0")

    lazy val a2 = Project("a", file("subA"),
      settings = Defaults.defaultSettings ++ Seq(name := "Additional", version := "1.0")
    )

Select Defaults
~~~~~~~~~~~~~~~

Web support is now split out into a plugin. With the plugin declared,
its settings can be selected like:

::

    import sbt_
    import Keys._

    object MyBuild extends Build {
      lazy val projectA = Project("a", file("subA"), settings = Web.webSettings)
    }

Settings defined in ``.sbt`` files are appended to the settings for each
``Project`` definition.

Build-level Settings
~~~~~~~~~~~~~~~~~~~~

Lastly, settings can be defined for the entire build. In general, these
are used when a setting is not defined for a project. These settings are
declared either by augmenting ``Build.settings`` or defining settings in
the scope of the current build. For example, to set the shell prompt to
be the id for the current project, the following setting can be added to
a ``.sbt`` file:

::

    shellPrompt in ThisBuild := { s => Project.extract(s).currentProject.id + "> " }

(The value is a function ``State => String``. ``State`` contains
everything about the build and will be discussed elsewhere.)
Alternatively, the setting can be defined in ``Build.settings``:

::

    import sbt._
    import Keys._

    object MyBuild extends Build {
      override lazy val settings = super.settings :+
        (shellPrompt := { s => Project.extract(s).currentProject.id + "> " })
      ...
    }

Project Relationships
---------------------

There are three kinds of project relationships in sbt. These are
described by execution, classpath, and configuration dependencies.

Project References
~~~~~~~~~~~~~~~~~~

When defining a dependency on another project, you provide a
``ProjectReference``. In the simplest case, this is a ``Project``
object. (Technically, there is an implicit conversion
``Project => ProjectReference``) This indicates a dependency on a
project within the same build. It is possible to declare a dependency on
a project in a directory separate from the current build, in a git
repository, or in a project packaged into a jar and accessible via
http/https. These are referred to as external builds and projects. You
can reference the root project in an external build with
``RootProject``:

``scala RootProject( file("/home/user/a-project") ) RootProject( uri("git://github.com/dragos/dupcheck.git") )``
or a specific project within the external build can be referenced using
a ``ProjectRef``:

::

    ProjectRef( uri("git://github.com/dragos/dupcheck.git"), "project-id")

The fragment part of the git URI can be used to specify a specific
branch or tag. For example:

::

    RootProject( uri("git://github.com/typesafehub/sbteclipse.git#v1.2") )

Ultimately, a ``RootProject`` is resolved to a ``ProjectRef`` once the
external project is loaded. Additionally, there are implicit conversions
``URI => RootProject`` and ``File => RootProject`` so that URIs and
Files can be used directly. External, remote builds are retrieved or
checked out to a staging directory in the user's ``.sbt`` directory so
that they can be manipulated like local builds. Examples of using
project references follow in the next sections.

When using external projects, the ``sbt.boot.directory`` should be set
(see [[Setup\|Getting Started Setup]]) so that unnecessary
recompilations do not occur (see gh-35).

Execution Dependency
~~~~~~~~~~~~~~~~~~~~

If project A has an execution dependency on project B, then when you
execute a task on project A, it will also be run on project B. No
ordering of these tasks is implied. An execution dependency is declared
using the ``aggregate`` method on ``Project``. For example:

::

    lazy val root = Project(...) aggregate(sub1)
    lazy val sub1 = Project(...) aggregate(sub2)
    lazy val sub2 = Project(...) aggregate(ext)
    lazy val ext = uri("git://github.com/dragos/dupcheck.git")

If 'clean' is executed on ``sub2``, it will also be executed on ``ext``
(the locally checked out version). If 'clean' is executed on ``root``,
it will also be executed on ``sub1``, ``sub2``, and ``ext``.

Aggregation can be controlled more finely by configuring the
``aggregate`` setting. This setting is of type ``Aggregation``:

::

    sealed trait Aggregation 
    final case class Implicit(enabled: Boolean) extends Aggregation
    final class Explicit(val deps: Seq[ProjectReference], val transitive: Boolean) extends Aggregation

This key can be set in any scope, including per-task scopes. By default,
aggregation is disabled for ``run``, ``console-quick``, ``console``, and
``console-project``. Re-enabling it from the command line for the
current project for ``run`` would look like:

::

    > set aggregate in run := true

(There is an implicit ``Boolean => Implicit`` where ``true`` translates
to ``Implicit(true)`` and ``false`` translates to ``Implicit(false)``).
Similarly, aggregation can be disabled for the current project using:

::

    > set aggregate in clean := false

``Explicit`` allows finer control over the execution dependencies and
transitivity. An instance is normally constructed using
``Aggregation.apply``. No new projects may be introduced here (that is,
internal references have to be defined already in the Build's
``projects`` and externals must be a dependency in the Build
definition). For example, to declare that ``root/clean`` aggregates
``sub1/clean`` and ``sub2/clean`` intransitively (that is, excluding
``ext`` even though ``sub2`` aggregates it):

::

    > set aggregate in clean := Aggregation(Seq(sub1, sub2), transitive = false)

Classpath Dependencies
~~~~~~~~~~~~~~~~~~~~~~

A classpath dependency declares that a project needs the full classpath
of another project on its classpath. Typically, this implies that the
dependency will ensure its classpath is up-to-date, such as by fetching
dependencies and recompiling modified sources.

A classpath dependency declaration consists of a project reference and
an optional configuration mapping. For example, to use project b's
``compile`` configuration from project a's ``test`` configuration:

``scala lazy val a = Project(...) dependsOn(b % "test->compile") lazy val b = Project(...)``
``"test->compile"`` may be shortened to ``"test"`` in this case. The
``%`` call may be omitted, in which case the mapping is
``"compile->compile"`` by default.

A useful configuration declaration is ``test->test``. This means to use
a dependency's test classes on the dependent's test classpath.

Multiple declarations may be separated by a semicolon. For example, the
following says to use the main classes of ``b`` for the compile
classpath of ``a`` as well as the test classes of ``b`` for the test
classpath of ``a``:

::

    lazy val a = Project(...) dependsOn(b % "compile;test->test")
    lazy val b = Project(...)

Configuration Dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~

Suppose project A has a configuration dependency on project B. If a
setting is not found on project A, it will be looked up in project B.
This is one aspect of delegation and will be described in detail
elsewhere.
