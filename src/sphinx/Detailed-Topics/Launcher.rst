======================
Launcher Specification
======================

The sbt launcher component is a self-contained jar that boots a Scala
application without Scala or the application already existing on the
system. The only prerequisites are the launcher jar itself, an optional
configuration file, and a java runtime version 1.6 or greater.

Overview
========

A user downloads the launcher jar and creates a script to run it. In
this documentation, the script will be assumed to be called `launch`.
For Unix, the script would look like: `java -jar sbt-launcher.jar "$@"`

The user then downloads the configuration file for the application (call
it `my.app.configuration`) and creates a script to launch it (call it
`myapp`): `launch @my.app.configuration "$@"`

The user can then launch the application using `myapp arg1 arg2 ...`

Like the launcher used to distribute `sbt`, the downloaded launcher
jar will retrieve Scala and the application according to the provided
configuration file. The versions may be fixed or read from a different
configuration file (the location of which is also configurable). The
location to which the Scala and application jars are downloaded is
configurable as well. The repositories searched are configurable.
Optional initialization of a properties file on launch is configurable.

Once the launcher has downloaded the necessary jars, it loads the
application and calls its entry point. The application is passed
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

Configuration
-------------

The launcher may be configured in one of the following ways in
increasing order of precedence:

-  Replace the `/sbt/sbt.boot.properties` file in the jar
-  Put a configuration file named `sbt.boot.properties` on the
   classpath. Put it in the classpath root without the `/sbt` prefix.
-  Specify the location of an alternate configuration on the command
   line, either as a path or an absolute URI. This can be done by
   either specifying the location as the system property
   `sbt.boot.properties` or as the first argument to the launcher
   prefixed by `@`. The system property has lower precedence.
   Resolution of a relative path is first attempted against the current
   working directory, then against the user's home directory, and then
   against the directory containing the launcher jar. An error is
   generated if none of these attempts succeed.

Syntax
~~~~~~

The configuration file is line-based, read as UTF-8 encoded, and defined
by the following grammar. `nl` is a newline or end of file and
`'text'` is plain text without newlines or the surrounding delimiters
(such as parentheses or square brackets):

.. productionlist::
    configuration: `scala` `app` `repositories` `boot` `log` `appProperties`
    scala: "[" "scala" "]" `nl` `version` `nl` `classifiers` `nl`
    app: "[" "app" "]" `nl` `org` `nl` `name` `nl` `version` `nl` `components` `nl` `class` `nl` `crossVersioned` `nl` `resources` `nl` `classifiers` `nl`
    repositories: "[" "repositories" "]" `nl` (`repository` `nl`)*
    boot: "[" "boot" "]" `nl` `directory` `nl` `bootProperties` `nl` `search` `nl` `promptCreate` `nl` `promptFill` `nl` `quickOption` `nl`
    log: "["' "log" "]" `nl` `logLevel` `nl`
    appProperties: "[" "app-properties" "]" `nl` (property `nl`)*
    ivy: "[" "ivy" "]" `nl` `homeDirectory` `nl` `checksums` `nl` `overrideRepos` `nl` `repoConfig` `nl`
    directory: "directory" ":" `path`
    bootProperties: "properties" ":" `path`
    search: "search" ":" ("none" | "nearest" | "root-first" | "only" ) ("," `path`)*
    logLevel: "level" ":" ("debug" | "info" | "warn" | "error")
    promptCreate: "prompt-create"  ":"  `label`
    promptFill: "prompt-fill" ":" `boolean`
    quickOption: "quick-option" ":" `boolean`
    version: "version" ":" `versionSpecification`
    versionSpecification: `readProperty` | `fixedVersion`
    readProperty: "read"  "(" `propertyName` ")"  "[" `default` "]"
    fixedVersion: text
    classifiers: "classifiers" ":" text ("," text)*
    homeDirectory: "ivy-home" ":" `path`
    checksums: "checksums" ":" `checksum` ("," `checksum`)*
    overrideRepos: "override-build-repos" ":" `boolean`
    repoConfig: "repository-config" ":" `path`
    org: "org" ":" text
    name: "name" ":" text
    class: "class" ":" text
    components: "components" ":" `component` ("," `component`)*
    crossVersioned: "cross-versioned" ":"  ("true" | "false" | "none" | "binary" | "full")
    resources: "resources" ":" `path` ("," `path`)*
    repository: ( `predefinedRepository` | `customRepository` ) `nl`
    predefinedRepository: "local" | "maven-local" | "maven-central"
    customRepository: `label` ":" `url` [ ["," `ivyPattern`] ["," `artifactPattern`] [", mavenCompatible"] [", bootOnly"]]
    property: `label` ":" `propertyDefinition` ("," `propertyDefinition`)*
    propertyDefinition: `mode` "=" (`set` | `prompt`)
    mode: "quick" | "new" | "fill"
    set: "set" "(" value ")"
    prompt: "prompt"  "(" `label` ")" ("[" `default` "]")?
    boolean: "true" | "false"
    nl: "\r\n" | "\n" | "\r"
    path: text
    propertyName: text
    label: text
    default: text
    checksum: text
    ivyPattern: text
    artifactPattern: text
    url: text
    component: text

In addition to the grammar specified here, property values may include
variable substitutions. A variable substitution has one of these forms:

-  `${variable.name}`
-  `${variable.name-default}`

where `variable.name` is the name of a system property. If a system
property by that name exists, the value is substituted. If it does not
exists and a default is specified, the default is substituted after
recursively substituting variables in it. If the system property does
not exist and no default is specified, the original string is not
substituted.

Example
~~~~~~~

The default configuration file for sbt looks like:

.. parsed-literal::

    [scala]
      version: ${sbt.scala.version-auto}

    [app]
      org: ${sbt.organization-org.scala-sbt}
      name: sbt
      version: ${sbt.version-read(sbt.version)[\ |release|\ ]}
      class: ${sbt.main.class-sbt.xMain}
      components: xsbti,extra
      cross-versioned: ${sbt.cross.versioned-false}

    [repositories]
      local
      typesafe-ivy-releases: http://repo.typesafe.com/typesafe/ivy-releases/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext], bootOnly
      maven-central
      sonatype-snapshots: https://oss.sonatype.org/content/repositories/snapshots

    [boot]
      directory: ${sbt.boot.directory-${sbt.global.base-${user.home}/.sbt}/boot/}

    [ivy]
      ivy-home: ${sbt.ivy.home-${user.home}/.ivy2/}
      checksums: ${sbt.checksums-sha1,md5}
      override-build-repos: ${sbt.override.build.repos-false}
      repository-config: ${sbt.repository.config-${sbt.global.base-${user.home}/.sbt}/repositories}

Semantics
~~~~~~~~~

The `scala.version` property specifies the version of Scala used to
run the application. If the application is not cross-built, this may be
set to `auto` and it will be auto-detected from the application's
dependencies. If specified, the `scala.classifiers` property defines
classifiers, such as 'sources', of extra Scala artifacts to retrieve.

The `app.org`, `app.name`, and `app.version` properties specify
the organization, module ID, and version of the application,
respectively. These are used to resolve and retrieve the application
from the repositories listed in `[repositories]`. If
`app.cross-versioned` is `binary`, the resolved module ID is
`{app.name+'_'+CrossVersion.binaryScalaVersion(scala.version)}`.
If `app.cross-versioned` is `true` or `full`, the resolved module ID is
`{app.name+'_'+scala.version}`. The `scala.version` property must be
specified and cannot be `auto` when cross-versioned. The paths given
in `app.resources` are added to the application's classpath. If the
path is relative, it is resolved against the application's working
directory. If specified, the `app.classifiers` property defines
classifiers, like 'sources', of extra artifacts to retrieve for the
application.

Jars are retrieved to the directory given by `boot.directory`. By
default, this is an absolute path that is shared by all launched
instances on the machine. If multiple versions access it simultaneously,
you might see messages like:

.. code-block:: console

      Waiting for lock on <lock-file> to be available...

This boot directory may be relative to the current directory instead. In
this case, the launched application will have a separate boot directory
for each directory it is launched in.

The `boot.properties` property specifies the location of the
properties file to use if `app.version` or `scala.version` is
specified as `read`. The `prompt-create`, `prompt-fill`, and
`quick-option` properties together with the property definitions in
`[app.properties]` can be used to initialize the `boot.properties`
file.

The `app.class` property specifies the name of the entry point to the
application. An application entry point must be a public class with a
no-argument constructor that implements `xsbti.AppMain`. The
`AppMain` interface specifies the entry method signature `run(AppConfiguration configuration)`.
The `run` method is passed an instance of `xsbti.AppConfiguration`, which provides
access to the startup environment. `AppConfiguration` also provides an
interface to retrieve other versions of Scala or other applications.
Finally, the return type of the `run` method is `xsbti.MainResult`,
which has two subtypes: `xsbti.Reboot` and `xsbti.Exit`. To exit
with a specific code, return an instance of `xsbti.Exit` with the
code. To restart the application, return an instance of
`xsbti.Reboot`. You can change some aspects of the configuration with a reboot,
such as the version of Scala, the application ID, and the arguments.

The `ivy.cache-directory` property provides an alternative location
for the Ivy cache used by the launcher. This does not automatically set
the Ivy cache for the application, but the application is provided this
location through the `AppConfiguration` instance.

The `checksums` property selects the checksum algorithms - `sha1` or `md5` -
that are used to verify artifacts downloaded by the launcher.

The `override-build-repos` is a flag that can inform the application that
the repositories configured for the launcher should be used in the application.
If `repository-config` is defined, the file it specifies should contain a
`[repositories]` section that is used in place of the section in the
original configuration file.

Execution
---------

On startup, the launcher searches for its configuration in the order
described in the `Configuration` section and then parses it. If either the
Scala version or the application version are specified as 'read', the
launcher determines them in the following manner. The file given by the
`boot.properties` property is read as a Java properties file to obtain
the version. The expected property names are `${app.name}.version` for
the application version (where `${app.name}` is replaced with the
value of the `app.name` property from the boot configuration file) and
`scala.version` for the Scala version. If the properties file does not
exist, the default value provided is used. If no default was provided,
an error is generated.

Once the final configuration is resolved, the launcher proceeds to
obtain the necessary jars to launch the application. The
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

    ${boot.directory}/${scala.version}/${app.org}/${app.name}/${sbt.version}

Once all required code is downloaded, the class loaders are set up. The
launcher creates a class loader for the requested version of Scala. It
then creates a child class loader containing the jars for the requested
`app.components` and with the paths specified in `app.resources`. An
application that does not use components will have all of its jars in
this class loader.

The main class for the application is then instantiated. It must be a
public class with a public no-argument constructor and must conform to
`xsbti.AppMain`. The `run` method is invoked and execution passes to the
application. The argument to the `run` method provides configuration
information and a callback to obtain a class loader for any version of
Scala that can be obtained from a repository in `[repositories]`. The
return value of the `run` method determines what is done after the
application executes. It can specify that the launcher should restart
the application or that it should exit with the provided exit code.
See Semantics section above.

Creating a Launched Application
-------------------------------

This section shows how to make an application that is launched by this
launcher. First, declare a dependency on the `launcher-interface`. Do not
declare a dependency on the `launcher` itself. The `launcher` interface
consists strictly of Java interfaces in order to avoid binary
incompatibility between the version of Scala used to compile the
launcher and the version used to compile your application. The launcher
interface class will be provided by the launcher, so it is only a
compile-time dependency. If you are building with sbt, your dependency
definition would be:

.. parsed-literal::

      libraryDependencies += "org.scala-sbt" % "launcher-interface" % "|release|" % "provided"

      resolvers += sbtResolver.value

`sbtResolver` is a setting that provides a resolver for obtaining sbt as a dependency.

Make the entry point to your class implement `xsbti.AppMain`. An example
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
            println(s"Hello world!  Running Scala $scalaVersion.")
            configuration.arguments.foreach(println)

            // demonstrate the ability to reboot the application into different versions of Scala
            // and how to return the code to exit with
            scalaVersion match
            {
                case "2.9.3" =>
                    new xsbti.Reboot {
                        def arguments = configuration.arguments
                        def baseDirectory = configuration.baseDirectory
                        def scalaVersion = "2.10.2"
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
available.

Running an Application
----------------------

As mentioned above, there are a few options to actually run the
application. The first involves providing a modified jar for download.
The second requires providing a configuration file for download.

-  Replace the `/sbt/sbt.boot.properties` file in the launcher's jar and
   distribute the modified jar. The user would need a script to run
   `java -jar your-launcher.jar arg1 arg2 ...`.
-  A user downloads the launcher's jar and you provide the configuration
   file.

   -  The user needs to run `java -Dsbt.boot.properties=your.boot.properties -jar launcher.jar`.
   -  The user already has a script to run the launcher (call it
      `launch`) so she needs to run `launch @your.boot.properties your-arg-1 your-arg-2`
