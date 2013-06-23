=============
Build Loaders
=============

Build loaders are the means by which sbt resolves, builds, and
transforms build definitions. Each aspect of loading may be customized
for special applications. Customizations are specified by overriding the
*buildLoaders* methods of your build definition's Build object. These
customizations apply to external projects loaded by the build, but not
the (already loaded) Build in which they are defined. Also documented on
this page is how to manipulate inter-project dependencies from a
setting.

Custom Resolver
---------------

The first type of customization introduces a new resolver. A resolver
provides support for taking a build URI and retrieving it to a local
directory on the filesystem. For example, the built-in resolver can
checkout a build using git based on a git URI, use a build in an
existing local directory, or download and extract a build packaged in a
jar file. A resolver has type:

::

    ResolveInfo => Option[() => File]

The resolver should return None if it cannot handle the URI or Some
containing a function that will retrieve the build. The ResolveInfo
provides a staging directory that can be used or the resolver can
determine its own target directory. Whichever is used, it should be
returned by the loading function. A resolver is registered by passing it
to *BuildLoader.resolve* and overriding *Build.buildLoaders* with the
result:

::

    ...
    object Demo extends Build {
      ...
      override def buildLoaders =
        BuildLoader.resolve(demoResolver) ::
        Nil

      def demoResolver: BuildLoader.ResolveInfo => Option[() => File] = ...

    }

API Documentation
~~~~~~~~~~~~~~~~~

Relevant API documentation for custom resolvers:

-  `ResolveInfo <../../api/index.html#sbt.BuildLoader$$ResolveInfo>`_
-  `BuildLoader <../../api/sbt/BuildLoader$.html>`_

Full Example
~~~~~~~~~~~~

::

    import sbt._
    import Keys._

    object Demo extends Build
    {
      // Define a project that depends on an external project with a custom URI
      lazy val root = Project("root", file(".")).dependsOn(
        uri("demo:a")
      )

      // Register the custom resolver
      override def buildLoaders = 
        BuildLoader.resolve(demoResolver) ::
        Nil

      // Define the custom resolver, which handles the 'demo' scheme.
      // The resolver's job is to produce a directory containing the project to load.
      // A subdirectory of info.staging can be used to create new local
      //   directories, such as when doing 'git clone ...'
      def demoResolver(info: BuildLoader.ResolveInfo): Option[() => File] =
        if(info.uri.getScheme != "demo") 
          None
        else
        {
          // Use a subdirectory of the staging directory for the new local build.
          // The subdirectory name is derived from a hash of the URI,
          //   and so identical URIs will resolve to the same directory (as desired).
          val base = RetrieveUnit.temporary(info.staging, info.uri)

          // Return a closure that will do the actual resolution when requested.
          Some(() => resolveDemo(base, info.uri.getSchemeSpecificPart))
        }

      // Construct a sample project on the fly with the name specified in the URI.
      def resolveDemo(base: File, ssp: String): File =
      {
        // Only create the project if it hasn't already been created.
        if(!base.exists)
          IO.write(base / "build.sbt", template.format(ssp))
        base
      }

      def template =  """
    name := "%s"

    version := "1.0"
    """
    }

Custom Builder
--------------

Once a project is resolved, it needs to be built and then presented to
sbt as an instance of ``sbt.BuildUnit``. A custom builder has type:

::

    BuildInfo => Option[() => BuildUnit] 

A builder returns None if it does not want to handle the build
identified by the ``BuildInfo``. Otherwise, it provides a function that
will load the build when evaluated. Register a builder by passing it to
*BuildLoader.build* and overriding *Build.buildLoaders* with the result:

::

    ...
    object Demo extends Build {
      ...
      override def buildLoaders =
        BuildLoader.build(demoBuilder) ::
        Nil

      def demoBuilder: BuildLoader.BuildInfo => Option[() => BuildUnit] = ...

    }

API Documentation
~~~~~~~~~~~~~~~~~

Relevant API documentation for custom builders:

-  `BuildInfo <../../api/sbt/BuildLoader$$BuildInfo.html>`_
-  `BuildLoader <../../api/sbt/BuildLoader$.html>`_
-  `BuildUnit <../../api/index.html#sbt.Load$$BuildUnit>`_

Example
~~~~~~~

This example demonstrates the structure of how a custom builder could
read configuration from a pom.xml instead of the standard .sbt files and
project/ directory.

::

        ... imports ...

    object Demo extends Build
    {
      lazy val root = Project("root", file(".")) dependsOn( file("basic-pom-project") )

      override def buildLoaders =
        BuildLoader.build(demoBuilder) ::
        Nil

      def demoBuilder: BuildInfo => Option[() => BuildUnit] = info =>
        if(pomFile(info).exists)
          Some(() => pomBuild(info))
        else
          None

      def pomBuild(info: BuildInfo): BuildUnit =
      {
        val pom = pomFile(info)
        val model = readPom(pom)

        val n = Project.normalizeProjectID(model.getName)
        val base = Option(model.getProjectDirectory) getOrElse info.base
        val root = Project(n, base) settings( pomSettings(model) : _*)
        val build = new Build { override def projects = Seq(root) }
        val loader = this.getClass.getClassLoader
        val definitions = new LoadedDefinitions(info.base, Nil, loader, build :: Nil, Nil)
        val plugins = new LoadedPlugins(info.base / "project", Nil, loader, Nil, Nil)
        new BuildUnit(info.uri, info.base, definitions, plugins)
      }
        
      def readPom(file: File): Model = ...
      def pomSettings(m: Model): Seq[Setting[_]] = ...
      def pomFile(info: BuildInfo): File = info.base / "pom.xml"

Custom Transformer
------------------

Once a project has been loaded into an ``sbt.BuildUnit``, it is
transformed by all registered transformers. A custom transformer has
type:

::

    TransformInfo => BuildUnit

A transformer is registered by passing it to *BuildLoader.transform* and
overriding *Build.buildLoaders* with the result:

::

    ...
    object Demo extends Build {
      ...
      override def buildLoaders =
        BuildLoader.transform(demoTransformer) ::
        Nil

      def demoBuilder: BuildLoader.TransformInfo => BuildUnit = ...

    }

API Documentation
~~~~~~~~~~~~~~~~~

Relevant API documentation for custom transformers:

-  `TransformInfo <../../api/index.html#sbt.BuildLoader$$TransformInfo>`_
-  `BuildLoader <../../api/sbt/BuildLoader$.html>`_
-  `BuildUnit <../../api/index.html#sbt.Load$$BuildUnit>`_

Manipulating Project Dependencies in Settings
=============================================

The ``buildDependencies`` setting, in the Global scope, defines the
aggregation and classpath dependencies between projects. By default,
this information comes from the dependencies defined by ``Project``
instances by the ``aggregate`` and ``dependsOn`` methods. Because
``buildDependencies`` is a setting and is used everywhere dependencies
need to be known (once all projects are loaded), plugins and build
definitions can transform it to manipulate inter-project dependencies at
setting evaluation time. The only requirement is that no new projects
are introduced because all projects are loaded before settings get
evaluated. That is, all Projects must have been declared directly in a
Build or referenced as the argument to ``Project.aggregate`` or
``Project.dependsOn``.

The BuildDependencies type
--------------------------

The type of the ``buildDependencies`` setting is
`BuildDependencies </api/sbt/BuildDependencies.html>`_.
``BuildDependencies`` provides mappings from a project to its aggregate
or classpath dependencies. For classpath dependencies, a dependency has
type ``ClasspathDep[ProjectRef]``, which combines a ``ProjectRef`` with
a configuration (see `ClasspathDep <../../api/sbt/ClasspathDep.html>`_
and `ProjectRef <../../api/sbt/ProjectRef.html>`_). For aggregate
dependencies, the type of a dependency is just ``ProjectRef``.

The API for ``BuildDependencies`` is not extensive, covering only a
little more than the minimum required, and related APIs have more of an
internal, unpolished feel. Most manipulations consist of modifying the
relevant map (classpath or aggregate) manually and creating a new
``BuildDependencies`` instance.

Example
~~~~~~~

As an example, the following replaces a reference to a specific build
URI with a new URI. This could be used to translate all references to a
certain git repository to a different one or to a different mechanism,
like a local directory.

::

    buildDependencies in Global ~= {  deps =>
      val oldURI = uri("...") // the URI to replace
      val newURI = uri("...") // the URI replacing oldURI
      def substitute(dep: ClasspathDep[ProjectRef]): ClasspathDep[ProjectRef] =
        if(dep.project.build == oldURI)
          ResolvedClasspathDependency(ProjectRef(newURI, dep.project.project), dep.configuration)
        else
          dep
      val newcp = 
        for( (proj, deps) <- deps.cp) yield
          (proj, deps map substitute)
      new BuildDependencies(newcp, deps.aggregate)
    }

It is not limited to such basic translations, however. The configuration
a dependency is defined in may be modified and dependencies may be added
or removed. Modifying ``buildDependencies`` can be combined with
modifying ``libraryDependencies`` to convert binary dependencies to and
from source dependencies, for example.
