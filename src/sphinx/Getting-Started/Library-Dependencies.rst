====================
Library Dependencies
====================

This page assumes you've read the earlier Getting Started pages, in
particular :doc:`.sbt build definition <Basic-Def>`,
:doc:`scopes <Scopes>`, and :doc:`more about settings <More-About-Settings>`.

Library dependencies can be added in two ways:

-  *unmanaged dependencies* are jars dropped into the ``lib`` directory
-  *managed dependencies* are configured in the build definition and
   downloaded automatically from repositories

Unmanaged dependencies
----------------------

Most people use managed dependencies instead of unmanaged. But unmanaged
can be simpler when starting out.

Unmanaged dependencies work like this: add jars to ``lib`` and they will
be placed on the project classpath. Not much else to it!

You can place test jars such as
`ScalaCheck <https://github.com/rickynils/scalacheck>`_,
`specs <http://code.google.com/p/specs/>`_, and
`ScalaTest <http://www.scalatest.org/>`_ in ``lib`` as well.

Dependencies in ``lib`` go on all the classpaths (for ``compile``,
``test``, ``run``, and ``console``). If you wanted to change the
classpath for just one of those, you would adjust
``dependencyClasspath in Compile`` or ``dependencyClasspath in Runtime``
for example. You could use ``~=`` to get the previous classpath value,
filter some entries out, and return a new classpath value. See :doc:`more about settings <More-About-Settings>`
for details of ``~=``.

There's nothing to add to ``build.sbt`` to use unmanaged dependencies,
though you could change the ``unmanaged-base`` key if you'd like to use
a different directory rather than ``lib``.

To use ``custom_lib`` instead of ``lib``:

::

    unmanagedBase <<= baseDirectory { base => base / "custom_lib" }

``baseDirectory`` is the project's root directory, so here you're
changing ``unmanagedBase`` depending on ``baseDirectory``, using ``<<=``
as explained in :doc:`more about settings <More-About-Settings>`.

There's also an ``unmanaged-jars`` task which lists the jars from the
``unmanaged-base`` directory. If you wanted to use multiple directories
or do something else complex, you might need to replace the whole
``unmanaged-jars`` task with one that does something else.

Managed Dependencies
--------------------

sbt uses `Apache Ivy <http://ant.apache.org/ivy/>`_ to implement managed
dependencies, so if you're familiar with Maven or Ivy, you won't have
much trouble.

The ``libraryDependencies`` key
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Most of the time, you can simply list your dependencies in the setting
``libraryDependencies``. It's also possible to write a Maven POM file or
Ivy configuration file to externally configure your dependencies, and
have sbt use those external configuration files. You can learn more
about that :ref:`here <external-maven-ivy>`.

Declaring a dependency looks like this, where ``groupId``,
``artifactId``, and ``revision`` are strings:

::

    libraryDependencies += groupID % artifactID % revision

or like this, where ``configuration`` is also a string:

::

    libraryDependencies += groupID % artifactID % revision % configuration

``libraryDependencies`` is declared in `Keys <../../sxr/Keys.scala.html>`_ like this:

::

    val libraryDependencies = SettingKey[Seq[ModuleID]]("library-dependencies", "Declares managed dependencies.")

The ``%`` methods create ``ModuleID`` objects from strings, then you add
those ``ModuleID`` to ``libraryDependencies``.

Of course, sbt (via Ivy) has to know where to download the module. If
your module is in one of the default repositories sbt comes with, this
will just work. For example, Apache Derby is in a default repository:

::

    libraryDependencies += "org.apache.derby" % "derby" % "10.4.1.3"

If you type that in ``build.sbt`` and then ``update``, sbt should
download Derby to ``~/.ivy2/cache/org.apache.derby/``. (By the way,
``update`` is a dependency of ``compile`` so there's no need to manually
type ``update`` most of the time.)

Of course, you can also use ``++=`` to add a list of dependencies all at
once:

::

    libraryDependencies ++= Seq(
        groupID % artifactID % revision,
        groupID % otherID % otherRevision
    )

And in rare cases you might find reasons to use ``:=``, ``<<=``,
``<+=``, etc. with ``libraryDependencies`` as well.

Getting the right Scala version with ``%%``
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you use ``groupID %% artifactID % revision`` rather than
``groupID % artifactID % revision`` (the difference is the double ``%%``
after the groupID), sbt will add your project's Scala version to the
artifact name. This is just a shortcut. You could write this without the
``%%``:

::

    libraryDependencies += "org.scala-tools" % "scala-stm_2.9.1" % "0.3"

Assuming the ``scalaVersion`` for your build is ``2.9.1``, the following
is identical:

::

    libraryDependencies += "org.scala-tools" %% "scala-stm" % "0.3"

The idea is that many dependencies are compiled for multiple Scala
versions, and you'd like to get the one that matches your project.

The complexity in practice is that often a dependency will work with a
slightly different Scala version; but ``%%`` is not smart about that. So
if the dependency is available for ``2.9.0`` but you're using
``scalaVersion := "2.9.1"``, you won't be able to use ``%%`` even though
the ``2.9.0`` dependency likely works. If ``%%`` stops working just go
see which versions the dependency is really built for, and hardcode the
one you think will work (assuming there is one).

See :doc:`/Detailed-Topics/Cross-Build` for some more detail on this.

Ivy revisions
~~~~~~~~~~~~~

The ``revision`` in ``groupID % artifactID % revision`` does not have to
be a single fixed version. Ivy can select the latest revision of a
module according to constraints you specify. Instead of a fixed revision
like ``"1.6.1"``, you specify ``"latest.integration"``, ``"2.9.+"``, or
``"[1.0,)"``. See the `Ivy
revisions <http://ant.apache.org/ivy/history/2.3.0-rc1/ivyfile/dependency.html#revision>`_
documentation for details.

Resolvers
~~~~~~~~~

Not all packages live on the same server; sbt uses the standard Maven2
repository by default. If your dependency isn't on one of the default
repositories, you'll have to add a *resolver* to help Ivy find it.

To add an additional repository, use

::

    resolvers += name at location

For example:

::

    resolvers += "Sonatype OSS Snapshots" at "https://oss.sonatype.org/content/repositories/snapshots"

The ``resolvers`` key is defined in
`Keys <../../sxr/Keys.scala.html>`_ like
this:

::

    val resolvers = SettingKey[Seq[Resolver]]("resolvers", "The user-defined additional resolvers for automatically managed dependencies.")

The ``at`` method creates a ``Resolver`` object from two strings.

sbt can search your local Maven repository if you add it as a
repository:

::

    resolvers += "Local Maven Repository" at "file://"+Path.userHome.absolutePath+"/.m2/repository"

See :doc:`/Detailed-Topics/Resolvers` for details on defining other types of repositories.

Overriding default resolvers
~~~~~~~~~~~~~~~~~~~~~~~~~~~~

``resolvers`` does not contain the default resolvers; only additional
ones added by your build definition.

``sbt`` combines ``resolvers`` with some default repositories to form
``external-resolvers``.

Therefore, to change or remove the default resolvers, you would need to
override ``external-resolvers`` instead of ``resolvers``.

Per-configuration dependencies
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

Often a dependency is used by your test code (in ``src/test/scala``,
which is compiled by the ``Test`` configuration) but not your main code.

If you want a dependency to show up in the classpath only for the
``Test`` configuration and not the ``Compile`` configuration, add
``% "test"`` like this:

::

    libraryDependencies += "org.apache.derby" % "derby" % "10.4.1.3" % "test"

Now, if you type ``show compile:dependency-classpath`` at the sbt
interactive prompt, you should not see derby. But if you type
``show test:dependency-classpath``, you should see the derby jar in the
list.

Typically, test-related dependencies such as
`ScalaCheck <https://github.com/rickynils/scalacheck>`_,
`specs <http://code.google.com/p/specs/>`_, and
`ScalaTest <http://www.scalatest.org/>`_ would be defined with
``% "test"``.

Next
====

There are some more details and tips-and-tricks related to library
dependencies :doc:`on this page </Detailed-Topics/Library-Management>`, if you didn't find an
answer on this introductory page.

If you're reading Getting Started in order, for now, you might move on
to read :doc:`.scala build definition <Full-Def>`.
