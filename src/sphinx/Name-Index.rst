Index
=====

This is an index of common methods, types, and values you might find in
an sbt build definition. For command names, see :doc:`Running </Getting-Started/Running>`.
For available plugins, see :doc:`the plugins list </Community/Community-Plugins>`.

Values and Types
----------------

Dependency Management
~~~~~~~~~~~~~~~~~~~~~

-  `ModuleID <../api/sbt/ModuleID.html>`_
   is the type of a dependency definition. See :doc:`/Detailed-Topics/Library-Management`.
-  `Artifact <../api/sbt/Artifact.html>`_
   represents a single artifact (such as a jar or a pom) to be built and
   published. See :doc:`/Detailed-Topics/Library-Management` and :doc:`/Detailed-Topics/Artifacts`.
-  A
   `Resolver <../api/sbt/Resolver.html>`_
   can resolve and retrieve dependencies. Many types of Resolvers can
   publish dependencies as well. A repository is a closely linked idea
   that typically refers to the actual location of the dependencies.
   However, sbt is not very consistent with this terminology and
   repository and resolver are occasionally used interchangeably.
-  A [ModuleConfiguration] defines a specific resolver to use for a
   group of dependencies.
-  A
   `Configuration <../api/sbt/Configuration.html>`_
   is a useful Ivy construct for grouping dependencies. See
   :ref:`ivy-configurations`. It is also used for :doc:`scoping settings </Getting-Started/Scopes>`.
-  `Compile`, `Test`, `Runtime`, `Provided`, and `Optional` are predefined :ref:`configurations <ivy-configurations>`.

Settings and Tasks
~~~~~~~~~~~~~~~~~~

-  A `Setting <../api/sbt/Init$Setting.html>`_
   describes how to initialize a specific setting in the build. It can
   use the values of other settings or the previous value of the setting
   being initialized.
-  A `SettingsDefinition <../api/sbt/Init$SettingsDefinition.html>`_
   is the actual type of an expression in a build.sbt. This allows
   either a single `Setting <../api/sbt/Init$Setting.html>`_
   or a sequence of settings (`SettingList <../api/sbt/Init$SettingList.html>`_)
   to be defined at once. The types in a :doc:`/Getting-Started/Full-Def` always use just a plain
   `Setting <../api/sbt/Init$Setting.html>`_.
-  `Initialize <../api/sbt/Init$Initialize.html>`_
   describes how to initialize a setting using other settings, but isn't
   bound to a particular setting yet. Combined with an initialization
   method and a setting to initialize, it produces a full
   `Setting <../api/sbt/Init$Setting.html>`_.
-  `TaskKey <../api/sbt/TaskKey.html>`_,
   `SettingKey <../api/sbt/SettingKey.html>`_,
   and
   `InputKey <../api/sbt/InputKey.html>`_
   are keys that represent a task or setting. These are not the actual
   tasks, but keys that are used to refer to them. They can be scoped to
   produce `ScopedTask <../api/sbt/ScopedTask.html>`_,
   `ScopedSetting <../api/sbt/ScopedSetting.html>`_,
   and `ScopedInput <../api/sbt/ScopedInput.html>`_.
   These form the base types that provide the Settings methods.
-  `InputTask <../api/sbt/InputTask.html>`_ parses and tab completes user input, producing a task to run.
-  `Task <../api/sbt/Task.html>`_ is
   the type of a task. A task is an action that runs on demand. This is
   in contrast to a setting, which is run once at project initialization.

Process
~~~~~~~

-  A `ProcessBuilder <../api/sbt/ProcessBuilder.html>`_
   is the type used to define a process. It provides combinators for
   building up processes from smaller processes.
-  A `Process <../api/sbt/Process.html>`_ represents the actual forked process.
-  The `Process companion object <../api/sbt/Process$.html>`_
   provides methods for constructing primitive processes.

Build Structure
~~~~~~~~~~~~~~~

-  `Build <../api/sbt/Build.html>`_ is
   the trait implemented for a :doc:`full definition </Getting-Started/Full-Def>`, which defines
   project relationships and settings.
-  `Plugin <../api/sbt/Plugin.html>`_ is the trait implemented for sbt :doc:`/Getting-Started/Using-Plugins`.
-  `Project <../api/sbt/Project.html>`_
   is both a trait and a companion object that declares a single module
   in a build. See :doc:`full definition </Getting-Started/Full-Def>`.
-  `Keys <../api/sbt/Keys$.html>`_ is
   an object that provides all of the built-in keys for settings and tasks.
-  `State <../api/sbt/State.html>`_ contains the full state for a build. It is mainly used by
   :doc:`/Extending/Commands` and sometimes :doc:`/Extending/Input-Tasks`. See also :doc:`/Extending/Build-State`.

Methods
-------

Settings and Tasks
~~~~~~~~~~~~~~~~~~

See the :doc:`Getting Started Guide </Getting-Started/Basic-Def>` for
details.

-  `:=`, `+=`, `++=` These
   construct a `Setting <../api/sbt/Init$Setting.html>`_,
   which is the fundamental type in the :doc:`settings </Getting-Started/Basic-Def>` system.
-  `value` This uses the value of another setting or task in the definition of a new setting or task.
   This method is special (it is a macro) and cannot be used except in the argument of one of the setting
   definition methods above (`:=`, ...) or in the standalone construction methods `Def.setting` and `Def.task`.
   See :doc:`more about settings </Getting-Started/More-About-Settings>` for details.
-  `in` specifies the `Scope <../api/sbt/Scope.html>`_ or part of the
   `Scope <../api/sbt/Scope.html>`_ of a setting being referenced. See :doc:`scopes </Getting-Started/Scopes>`.

File and IO
~~~~~~~~~~~

See `RichFile <../api/sbt/RichFile.html>`_,
`PathFinder <../api/sbt/PathFinder.html>`_,
and :doc:`/Detailed-Topics/Paths` for the full documentation.

-  `/` When called on a single File, this is `new File(x,y)`. For
   `Seq[File]`, this is applied for each member of the sequence..
-  `*` and `**` are methods for selecting children (`*`) or
   descendants (`**`) of a `File` or `Seq[File]` that match a
   filter.
-  `|`, `||`, `&&`, `&`, `-`, and `--` are methods for
   combining filters, which are often used for selecting `File`\ s.
   See
   `NameFilter <../api/sbt/NameFilter.html>`_
   and
   `FileFilter <../api/sbt/FileFilter.html>`_.
   Note that methods with these names also exist for other types, such
   as collections (like `Seq`) and
   `Parser <../api/sbt/complete/Parser.html>`_
   (see :doc:`/Detailed-Topics/Parsing-Input`).
-  `pair` Used to construct mappings from a `File` to another `File`
   or to a `String`. See :doc:`/Detailed-Topics/Mapping-Files`.
-  `get` forces a `PathFinder <../api/sbt/PathFinder.html>`_
   (a call-by-name data structure) to a strict `Seq[File]`
   representation. This is a common name in Scala, used by types like
   `Option`.

Dependency Management
~~~~~~~~~~~~~~~~~~~~~

See :doc:`/Detailed-Topics/Library-Management` for full documentation.

-  `%` This is used to build up a
   `ModuleID <../api/sbt/ModuleID.html>`_.
-  `%%` This is similar to `%` except that it identifies a
   dependency that has been :doc:`cross built </Detailed-Topics/Cross-Build>`.
-  `from` Used to specify the fallback URL for a dependency
-  `classifier` Used to specify the classifier for a dependency.
-  `at` Used to define a Maven-style resolver.
-  `intransitive` Marks a `dependency <../api/sbt/ModuleID.html>`_
   or `Configuration <../api/sbt/Configuration.html>`_
   as being intransitive.
-  `hide` Marks a
   `Configuration <../api/sbt/Configuration.html>`_
   as internal and not to be included in the published metadata.

Parsing
~~~~~~~

These methods are used to build up `Parser <../api/sbt/complete/Parser.html>`_\ s
from smaller `Parser <../api/sbt/complete/Parser.html>`_\ s.
They closely follow the names of the standard library's parser
combinators. See :doc:`/Detailed-Topics/Parsing-Input` for the full documentation. These are
used for :doc:`/Extending/Input-Tasks` and :doc:`/Extending/Commands`.

-  `~`, `~>`, `<~` Sequencing methods.
-  `??`, `?` Methods for making a Parser optional. `?` is postfix.
-  `id` Used for turning a Char or String literal into a Parser. It is
   generally used to trigger an implicit conversion to a Parser.
-  `|`, `||` Choice methods. These are common method names in Scala.
-  `^^^` Produces a constant value when a Parser matches.
-  `+`, `*` Postfix repetition methods. These are common method
   names in Scala.
-  `map`, `flatMap` Transforms the result of a Parser. These are
   common method names in Scala.
-  `filter` Restricts the inputs that a Parser matches on. This is a
   common method name in Scala.
-  `-` Prefix negation. Only matches the input when the original
   parser doesn't match the input.
-  `examples`, `token` Tab completion
-  `!!!` Provides an error message to use when the original parser
   doesn't match the input.

Processes
~~~~~~~~~

These methods are used to :doc:`fork external processes </Detailed-Topics/Process>`. Note
that this API has been included in the Scala standard library for
version 2.9.
`ProcessBuilder <../api/sbt/ProcessBuilder.html>`_
is the builder type and `Process <../api/sbt/Process.html>`_
is the type representing the actual forked process. The methods to
combine processes start with `#` so that they share the same
precedence.

-  `run`, `!`, `!!`, `!<`, `lines`, `lines_!` are different
   ways to start a process once it has been defined. The `lines`
   variants produce a `Stream[String]` to obtain the output lines.
-  `#<`, `#<<`, `#>` are used to get input for a process from a
   source or send the output of a process to a sink.
-  `#|` is used to pipe output from one process into the input of
   another.
-  `#||`, `#&&`, `###` sequence processes in different ways.

