=============
Update Report
=============

``update`` and related tasks produce a value of type
`sbt.UpdateReport <../../api/sbt/UpdateReport.html>`_
This data structure provides information about the resolved
configurations, modules, and artifacts. At the top level,
``UpdateReport`` provides reports of type ``ConfigurationReport`` for
each resolved configuration. A ``ConfigurationReport`` supplies reports
(of type ``ModuleReport``) for each module resolved for a given
configuration. Finally, a ``ModuleReport`` lists each successfully
retrieved ``Artifact`` and the ``File`` it was retrieved to as well as
the ``Artifact``\ s that couldn't be downloaded. This missing
``Arifact`` list is always empty for ``update``, which will fail if it is
non-empty. However, it may be non-empty for ``updateClassifiers`` and
``updateSbtClassifers``.

Filtering a Report and Getting Artifacts
========================================

A typical use of ``UpdateReport`` is to retrieve a list of files
matching a filter. A conversion of type
``UpdateReport => RichUpdateReport`` implicitly provides these methods
for ``UpdateReport``. The filters are defined by the
`DependencyFilter <../../api/sbt/DependencyFilter.html>`_,
`ConfigurationFilter <../../api/sbt/ConfigurationFilter.html>`_,
`ModuleFilter <../../api/sbt/ModuleFilter.html>`_,
and
`ArtifactFilter <../../api/sbt/ArtifactFilter.html>`_
types. Using these filter types, you can filter by the configuration
name, the module organization, name, or revision, and the artifact name,
type, extension, or classifier.

The relevant methods (implicitly on ``UpdateReport``) are:

::

      def matching(f: DependencyFilter): Seq[File]

      def select(configuration: ConfigurationFilter = ..., module: ModuleFilter = ..., artifact: ArtifactFilter = ...): Seq[File]

Any argument to ``select`` may be omitted, in which case all values are
allowed for the corresponding component. For example, if the
``ConfigurationFilter`` is not specified, all configurations are
accepted. The individual filter types are discussed below.

Filter Basics
-------------

Configuration, module, and artifact filters are typically built by
applying a ``NameFilter`` to each component of a ``Configuration``,
``ModuleID``, or ``Artifact``. A basic ``NameFilter`` is implicitly
constructed from a String, with ``*`` interpreted as a wildcard.

::

    import sbt._
    // each argument is of type NameFilter
    val mf: ModuleFilter = moduleFilter(organization = "*sbt*", name = "main" | "actions", revision = "1.*" - "1.0")

    // unspecified arguments match everything by default
    val mf: ModuleFilter = moduleFilter(organization = "net.databinder")

    // specifying "*" is the same as omitting the argument
    val af: ArtifactFilter = artifactFilter(name = "*", `type` = "source", extension = "jar", classifier = "sources")

    val cf: ConfigurationFilter = configurationFilter(name = "compile" | "test")

Alternatively, these filters, including a ``NameFilter``, may be
directly defined by an appropriate predicate (a single-argument function
returning a Boolean).

::

    import sbt._

    // here the function value of type String => Boolean is implicitly converted to a NameFilter
    val nf: NameFilter = (s: String) => s.startsWith("dispatch-")

    // a Set[String] is a function String => Boolean
    val acceptConfigs: Set[String] = Set("compile", "test")
    // implicitly converted to a ConfigurationFilter
    val cf: ConfigurationFilter = acceptConfigs

    val mf: ModuleFilter = (m: ModuleID) => m.organization contains "sbt"

    val af: ArtifactFilter = (a: Artifact) => a.classifier.isEmpty

ConfigurationFilter
-------------------

A configuration filter essentially wraps a ``NameFilter`` and is
explicitly constructed by the ``configurationFilter`` method:

::

    def configurationFilter(name: NameFilter = ...): ConfigurationFilter

If the argument is omitted, the filter matches all configurations.
Functions of type ``String => Boolean`` are implicitly convertible to a
``ConfigurationFilter``. As with ``ModuleFilter``, ``ArtifactFilter``,
and ``NameFilter``, the ``&``, ``|``, and ``-`` methods may be used to
combine ``ConfigurationFilter``\ s.

::

    import sbt._
    val a: ConfigurationFilter = Set("compile", "test")
    val b: ConfigurationFilter = (c: String) => c.startsWith("r")
    val c: ConfigurationFilter = a | b

(The explicit types are optional here.)

ModuleFilter
------------

A module filter is defined by three ``NameFilter``\ s: one for the
organization, one for the module name, and one for the revision. Each
component filter must match for the whole module filter to match. A
module filter is explicitly constructed by the ``moduleFilter`` method:

::

    def moduleFilter(organization: NameFilter = ..., name: NameFilter = ..., revision: NameFilter = ...): ModuleFilter

An omitted argument does not contribute to the match. If all arguments
are omitted, the filter matches all ``ModuleID``\ s. Functions of type
``ModuleID => Boolean`` are implicitly convertible to a
``ModuleFilter``. As with ``ConfigurationFilter``, ``ArtifactFilter``,
and ``NameFilter``, the ``&``, ``|``, and ``-`` methods may be used to
combine ``ModuleFilter``\ s:

::

    import sbt._
    val a: ModuleFilter = moduleFilter(name = "dispatch-twitter", revision = "0.7.8")
    val b: ModuleFilter = moduleFilter(name = "dispatch-*")
    val c: ModuleFilter = b - a

(The explicit types are optional here.)

ArtifactFilter
--------------

An artifact filter is defined by four ``NameFilter``\ s: one for the
name, one for the type, one for the extension, and one for the
classifier. Each component filter must match for the whole artifact
filter to match. An artifact filter is explicitly constructed by the
``artifactFilter`` method:

::

    def artifactFilter(name: NameFilter = ..., `type`: NameFilter = ..., extension: NameFilter = ..., classifier: NameFilter = ...): ArtifactFilter

Functions of type ``Artifact => Boolean`` are implicitly convertible to
an ``ArtifactFilter``. As with ``ConfigurationFilter``,
``ModuleFilter``, and ``NameFilter``, the ``&``, ``|``, and ``-``
methods may be used to combine ``ArtifactFilter``\ s:

::

    import sbt._
    val a: ArtifactFilter = artifactFilter(classifier = "javadoc")
    val b: ArtifactFilter = artifactFilter(`type` = "jar")
    val c: ArtifactFilter = b - a

(The explicit types are optional here.)

DependencyFilter
----------------

A ``DependencyFilter`` is typically constructed by combining other
``DependencyFilter``\ s together using ``&&``, ``||``, and ``--``.
Configuration, module, and artifact filters are ``DependencyFilter``\ s
themselves and can be used directly as a ``DependencyFilter`` or they
can build up a ``DependencyFilter``. Note that the symbols for the
``DependencyFilter`` combining methods are doubled up to distinguish
them from the combinators of the more specific filters for
configurations, modules, and artifacts. These double-character methods
will always return a ``DependencyFilter``, whereas the single character
methods preserve the more specific filter type. For example:

::

    import sbt._

    val df: DependencyFilter =
      configurationFilter(name = "compile" | "test") && artifactFilter(`type` = "jar") || moduleFilter(name = "dispatch-*")

Here, we used ``&&`` and ``||`` to combine individual component filters
into a dependency filter, which can then be provided to the
``UpdateReport.matches`` method. Alternatively, the
``UpdateReport.select`` method may be used, which is equivalent to
calling ``matches`` with its arguments combined with ``&&``.
