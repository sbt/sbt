======
Scopes
======

This page describes scopes. It assumes you've read and understood the
previous page, :doc:`.sbt build definition <Basic-Def>`.

The whole story about keys
--------------------------

:doc:`Previously <Basic-Def>` we pretended that a key like
``name`` corresponded to one entry in sbt's map of key-value pairs. This
was a simplification.

In truth, each key can have an associated value in more than one
context, called a "scope."

Some concrete examples:

-  if you have multiple projects in your build definition, a key can
   have a different value in each project.
-  the ``compile`` key may have a different value for your main sources
   and your test sources, if you want to compile them differently.
-  the ``package-options`` key (which contains options for creating jar
   packages) may have different values when packaging class files
   (``package-bin``) or packaging source code (``package-src``).

*There is no single value for a given key name*, because the value may
differ according to scope.

However, there is a single value for a given *scoped* key.

If you think about sbt processing a list of settings to generate a
key-value map describing the project, as :doc:`discussed earlier <Basic-Def>`,
the keys in that key-value map are *scoped* keys.
Each setting defined in the build definition (for example in
``build.sbt``) applies to a scoped key as well.

Often the scope is implied or has a default, but if the defaults are
wrong, you'll need to mention the desired scope in ``build.sbt``.

Scope axes
----------

A *scope axis* is a type, where each instance of the type can define its
own scope (that is, each instance can have its own unique values for
keys).

There are three scope axes:

-  Projects
-  Configurations
-  Tasks

Scoping by project axis
~~~~~~~~~~~~~~~~~~~~~~~

If you :doc:`put multiple projects in a single build <Multi-Project>`, each project needs its own settings. That is, keys can
be scoped according to the project.

The project axis can also be set to "entire build", so a setting applies
to the entire build rather than a single project. Build-level settings
are often used as a fallback when a project doesn't define a
project-specific setting.

Scoping by configuration axis
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

A *configuration* defines a flavor of build, potentially with its own
classpath, sources, generated packages, etc. The configuration concept
comes from Ivy, which sbt uses for :doc:`managed dependencies <Library-Dependencies>`, and from
`MavenScopes <http://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Dependency_Scope>`_.

Some configurations you'll see in sbt:

-  ``Compile`` which defines the main build (``src/main/scala``).
-  ``Test`` which defines how to build tests (``src/test/scala``).
-  ``Runtime`` which defines the classpath for the ``run`` task.

By default, all the keys associated with compiling, packaging, and
running are scoped to a configuration and therefore may work differently
in each configuration. The most obvious examples are the task keys
``compile``, ``package``, and ``run``; but all the keys which *affect*
those keys (such as ``source-directories`` or ``scalac-options`` or
``full-classpath``) are also scoped to the configuration.

Scoping by task axis
~~~~~~~~~~~~~~~~~~~~

Settings can affect how a task works. For example, the ``package-src``
task is affected by the ``package-options`` setting.

To support this, a task key (such as ``package-src``) can be a scope for
another key (such as ``package-options``).

The various tasks that build a package (``package-src``,
``package-bin``, ``package-doc``) can share keys related to packaging,
such as ``artifact-name`` and ``package-options``. Those keys can have
distinct values for each packaging task.

Global scope
------------

Each scope axis can be filled in with an instance of the axis type (for
example the task axis can be filled in with a task), or the axis can be
filled in with the special value ``Global``.

``Global`` means what you would expect: the setting's value applies to
all instances of that axis. For example if the task axis is ``Global``,
then the setting would apply to all tasks.

Delegation
----------

A scoped key may be undefined, if it has no value associated with it in
its scope.

For each scope, sbt has a fallback search path made up of other scopes.
Typically, if a key has no associated value in a more-specific scope,
sbt will try to get a value from a more general scope, such as the
``Global`` scope or the entire-build scope.

This feature allows you to set a value once in a more general scope,
allowing multiple more-specific scopes to inherit the value.

You can see the fallback search path or "delegates" for a key using the
``inspect`` command, as described below. Read on.

Referring to scoped keys when running sbt
-----------------------------------------

On the command line and in interactive mode, sbt displays (and parses)
scoped keys like this:

.. code-block:: text

    {<build-uri>}<project-id>/config:intask::key

-  ``{<build-uri>}<project-id>`` identifies the project axis. The
   ``<project-id>`` part will be missing if the project axis has "entire
   build" scope.
-  ``config`` identifies the configuration axis.
-  ``intask`` identifies the task axis.
-  ``key`` identifies the key being scoped.

``*`` can appear for each axis, referring to the ``Global`` scope.

If you omit part of the scoped key, it will be inferred as follows:

-  the current project will be used if you omit the project.
-  a key-dependent configuration will be auto-detected if you omit the
   configuration or task.

For more details, see :doc:`/Detailed-Topics/Inspecting-Settings`.

Examples of scoped key notation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

-  ``full-classpath``: just a key, so the default scopes are used:
   current project, a key-dependent configuration, and global task
   scope.
-  ``test:full-classpath``: specifies the configuration, so this is
   ``full-classpath`` in the ``test`` configuration, with defaults for
   the other two scope axes.
-  ``*:full-classpath``: specifies ``Global`` for the configuration,
   rather than the default configuration.
-  ``doc::full-classpath``: specifies the ``full-classpath`` key scoped
   to the ``doc`` task, with the defaults for the project and
   configuration axes.
-  ``{file:/home/hp/checkout/hello/}default-aea33a/test:full-classpath``
   specifies a project,
   ``{file:/home/hp/checkout/hello/}default-aea33a``, where the project
   is identified with the build ``{file:/home/hp/checkout/hello/}`` and
   then a project id inside that build ``default-aea33a``. Also
   specifies configuration ``test``, but leaves the default task axis.
-  ``{file:/home/hp/checkout/hello/}/test:full-classpath`` sets the
   project axis to "entire build" where the build is
   ``{file:/home/hp/checkout/hello/}``
-  ``{.}/test:full-classpath`` sets the project axis to "entire build"
   where the build is ``{.}``. ``{.}`` can be written ``ThisBuild`` in
   Scala code.
-  ``{file:/home/hp/checkout/hello/}/compile:doc::full-classpath`` sets
   all three scope axes.

Inspecting scopes
-----------------

In sbt's interactive mode, you can use the ``inspect`` command to
understand keys and their scopes. Try ``inspect test:full-classpath``:

.. code-block:: text

    $ sbt
    > inspect test:full-classpath
    [info] Task: scala.collection.Seq[sbt.Attributed[java.io.File]]
    [info] Description:
    [info]  The exported classpath, consisting of build products and unmanaged and managed, internal and external dependencies.
    [info] Provided by:
    [info]  {file:/home/hp/checkout/hello/}default-aea33a/test:full-classpath
    [info] Dependencies:
    [info]  test:exported-products
    [info]  test:dependency-classpath
    [info] Reverse dependencies:
    [info]  test:run-main
    [info]  test:run
    [info]  test:test-loader
    [info]  test:console
    [info] Delegates:
    [info]  test:full-classpath
    [info]  runtime:full-classpath
    [info]  compile:full-classpath
    [info]  *:full-classpath
    [info]  {.}/test:full-classpath
    [info]  {.}/runtime:full-classpath
    [info]  {.}/compile:full-classpath
    [info]  {.}/*:full-classpath
    [info]  */test:full-classpath
    [info]  */runtime:full-classpath
    [info]  */compile:full-classpath
    [info]  */*:full-classpath
    [info] Related:
    [info]  compile:full-classpath
    [info]  compile:full-classpath(for doc)
    [info]  test:full-classpath(for doc)
    [info]  runtime:full-classpath

On the first line, you can see this is a task (as opposed to a setting,
as explained in :doc:`.sbt build definition <Basic-Def>`).
The value resulting from the task will have type
``scala.collection.Seq[sbt.Attributed[java.io.File]]``.

"Provided by" points you to the scoped key that defines the value, in
this case
``{file:/home/hp/checkout/hello/}default-aea33a/test:full-classpath``
(which is the ``full-classpath`` key scoped to the ``test``
configuration and the ``{file:/home/hp/checkout/hello/}default-aea33a``
project).

"Dependencies" may not make sense yet; stay tuned for the :doc:`next page <More-About-Settings>`.

You can also see the delegates; if the value were not defined, sbt would
search through:

-  two other configurations (``runtime:full-classpath``,
   ``compile:full-classpath``). In these scoped keys, the project is
   unspecified meaning "current project" and the task is unspecified
   meaning ``Global``
-  configuration set to ``Global`` (``*:full-classpath``), since project
   is still unspecified it's "current project" and task is still
   unspecified so ``Global``
-  project set to ``{.}`` or ``ThisBuild`` (meaning the entire build, no
   specific project)
-  project axis set to ``Global`` (``*/test:full-classpath``) (remember,
   an unspecified project means current, so searching ``Global`` here is
   new; i.e. ``*`` and "no project shown" are different for the project
   axis; i.e. ``*/test:full-classpath`` is not the same as
   ``test:full-classpath``)
-  both project and configuration set to ``Global``
   (``*/*:full-classpath``) (remember that unspecified task means
   ``Global`` already, so ``*/*:full-classpath`` uses ``Global`` for all
   three axes)

Try ``inspect full-classpath`` (as opposed to the above example,
``inspect test:full-classpath``) to get a sense of the difference.
Because the configuration is omitted, it is autodetected as ``compile``.
``inspect compile:full-classpath`` should therefore look the same as
``inspect full-classpath``.

Try ``inspect *:full-classpath`` for another contrast.
``full-classpath`` is not defined in the ``Global`` configuration by
default.

Again, for more details, see :doc:`/Detailed-Topics/Inspecting-Settings`.

Referring to scopes in a build definition
-----------------------------------------

If you create a setting in ``build.sbt`` with a bare key, it will be
scoped to the current project, configuration ``Global`` and task
``Global``:

::

    name := "hello"

Run sbt and ``inspect name`` to see that it's provided by
``{file:/home/hp/checkout/hello/}default-aea33a/*:name``, that is, the
project is ``{file:/home/hp/checkout/hello/}default-aea33a``, the
configuration is ``*`` (meaning global), and the task is not shown
(which also means global).

``build.sbt`` always defines settings for a single project, so the
"current project" is the project you're defining in that particular
``build.sbt``. (For :doc:`multi-project builds <Multi-Project>`, each project has its own ``build.sbt``.)

Keys have an overloaded method called ``in`` used to set the scope. The
argument to ``in`` can be an instance of any of the scope axes. So for
example, though there's no real reason to do this, you could set the
name scoped to the ``Compile`` configuration:

::

    name in Compile := "hello"

or you could set the name scoped to the ``package-bin`` task (pointless!
just an example):

::

    name in packageBin := "hello"

or you could set the name with multiple scope axes, for example in the
``packageBin`` task in the ``Compile`` configuration:

::

    name in (Compile, packageBin) := "hello"

or you could use ``Global`` for all axes:

::

    name in Global := "hello"

(``name in Global`` implicitly converts the scope axis ``Global`` to a
scope with all axes set to ``Global``; the task and configuration are
already ``Global`` by default, so here the effect is to make the project
``Global``, that is, define ``*/*:name`` rather than
``{file:/home/hp/checkout/hello/}default-aea33a/*:name``)

If you aren't used to Scala, a reminder: it's important to understand
that ``in`` and ``:=`` are just methods, not magic. Scala lets you write
them in a nicer way, but you could also use the Java style:

::

    name.in(Compile).:=("hello")

There's no reason to use this ugly syntax, but it illustrates that these
are in fact methods.

When to specify a scope
-----------------------

You need to specify the scope if the key in question is normally scoped.
For example, the ``compile`` task, by default, is scoped to ``Compile``
and ``Test`` configurations, and does not exist outside of those scopes.

To change the value associated with the ``compile`` key, you need to
write ``compile in Compile`` or ``compile in Test``. Using plain
``compile`` would define a new compile task scoped to the current
project, rather than overriding the standard compile tasks which are
scoped to a configuration.

If you get an error like *"Reference to undefined setting"*, often
you've failed to specify a scope, or you've specified the wrong scope.
The key you're using may be defined in some other scope. sbt will try to
suggest what you meant as part of the error message; look for "Did you
mean compile:compile?"

One way to think of it is that a name is only *part* of a key. In
reality, all keys consist of both a name, and a scope (where the scope
has three axes). The entire expression
``packageOptions in (Compile, packageBin)`` is a key name, in other
words. Simply ``packageOptions`` is also a key name, but a different one
(for keys with no ``in``, a scope is implicitly assumed: current
project, global config, global task).

Next
----

Now that you understand scopes, you can :doc:`learn more about settings <More-About-Settings>`.
