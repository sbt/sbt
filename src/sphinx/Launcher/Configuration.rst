==========================
Sbt Launcher Configuration
==========================

The launcher may be configured in one of the following ways in
increasing order of precedence:

-  Replace the `/sbt/sbt.boot.properties` file in the launcher jar
-  Put a configuration file named `sbt.boot.properties` on the
   classpath. Put it in the classpath root without the `/sbt` prefix.
-  Specify the location of an alternate configuration on the command
   line, either as a path or an absolute URI. This can be done by
   either specifying the location as the system property
   `sbt.boot.properties` or as the first argument to the launcher
   prefixed by `'@'`. The system property has lower precedence.
   Resolution of a relative path is first attempted against the current
   working directory, then against the user's home directory, and then
   against the directory containing the launcher jar. 

An error is generated if none of these attempts succeed.

Example
~~~~~~~

The default configuration file for sbt as an application looks like:

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

Let's look at all the launcher configuration sections in detail:

1. Scala Configuration
----------------------
The `[scala]` section is used to configure the version of Scala.  
It has one property:

* `version` - The version of scala an application uses, or `auto` if the
  application is not cross-versioned.
* `classifiers` - The (optional) list of additional scala artifacts to resolve,
  e.g. `sources`.


2. Applicaiton Identification
-----------------------------
The `[app]` section configures how the launcher will look for your application
using the Ivy dependency manager.  It consists of the following properties:

* `org` - The organization associated with the Ivy module.
  (`groupId` in maven vernacular)
* `name` - The name of the Ivy module.  (`artifactId` in maven vernacular)
* `version` - The revision of the Ivy module.
* `class` - The name of the "entry point" into the application.  An entry
  point must be a class which meets one of the following critera
  - Extends the `xsbti.AppMain` interface.
  - Extends the `xsbti.ServerMain` interfaces.
  - Contains a method with the signature `static void main(String[])`
  - Contains a method with the signature `static int main(String[])`
  - Contains a method with the signature `static xsbti.Exit main(String[])`
* `components` - An optional list of additional components that Ivy should
  resolve.
* `cross-versioned` - An optional string denoting how this application is
  published.
  If `app.cross-versioned` is `binary`, the resolved module ID is
  `{app.name+'_'+CrossVersion.binaryScalaVersion(scala.version)}`.
  If `app.cross-versioned` is `true` or `full`, the resolved module ID is
  `{app.name+'_'+scala.version}`. The `scala.version` property must be
  specified and cannot be `auto` when cross-versioned.
* `resources` - An optional list of jar files that should be added to
  the application's classpath.
* `classifiers` - An optional list of additional classifiers that should be
  resolved with this application, e.g. `sources`.

3. Repositories Section
-----------------------
The `[repositories]` section configures where and how Ivy will look for
your application.  Each line denotes a repository where Ivy will look.

*Note: This section configured the default location where Ivy will look, but
this can be overriden via user configuration.*

There are several built-in strings that can be used for common repositories:

* `local` - the local ivy repository `~/.ivy2/local`.
* `maven-local` - The local maven repository `~/.ivy2/local`.
* `maven-central` - The maven central repository `repo.maven.org`.

Besides built in repositories, other repositories can be configured using
the following syntax:

.. parsed-literal::
  name: url(, pattern)(,descriptorOptional)(,skipConsistencyCheck)

The `name` property is an identifier which Ivy uses to cache modules
resolved from this location.   The `name` should be unique across all
repositories.

The `url` property is the base `url` where Ivy should look for modules.

The `pattern` property is an optional specification of *how* Ivy should
look for modules.   By default, the launcher assumes repositories are in
the maven style format.

The `skipConsistencyCheck` string is used to tell ivy not to validate checksums
and signatures of files it resolves.

4. The Boot section
-------------------
The `[boot]` section is used to configure where the sbt launcher will store
its cache and configuration information.  It consists of the following properties:

* `directory` -  The directory defined here is used to store all cached JARs
  resolved launcher.
* `properties` - (optional) A properties file to use for any `read` variables.

5. The Ivy section
------------------
The `[ivy]` section is used to configure the Ivy dependency manager for
resolving applications.  It consists of the following properties:

* `ivy-home` - The home directory for Ivy.  This determines where the
  `ivy-local` repository is located, and also where the ivy cache is
  stored.   Defaults to `~/.ivy2`
* `ivy.cache-directory` - provides an alternative location for the Ivy 
  cache used by the launcher. This does not automatically set the Ivy 
  cache for the application, but the application is provided this location 
  through the AppConfiguration instance.
* `checksums` - The comma-separated list of checksums that Ivy should use
  to verify artifacts have correctly resolved, e.g. `md5` or `sha1`.  
* `override-build-repos` - If this is set, then the `isOverrideRepositories`
  method on `xsbti.Launcher` interface will return its value.   The use of this
  method is application specific, but in the case of sbt denotes that the
  configuration of repositories in the launcher should override those used
  by any build.  Applications should respect this convention if they can.
* `repository-config` - This specifies a configuration location where
  ivy repositories can also be configured.  If this file exists, then its contents
  override the `[repositories]` section.


6. The Server Section
---------------------
When using the `--locate` feature of the launcher, this section configures
how a server is started.  It consists of the following properties:

* `lock` - The file that controls access to the running server.  This file
  will contain the active port used by a server and must be located on a
  a filesystem that supports locking.
* `jvmargs` - A file that contains line-separated JVM arguments that where
              use when starting the server. 
* `jvmprops` - The location of a properties file that will define override
  properties in the server.  All properties defined in this file will
  be set as `-D` java properties.

Variable Substitution
~~~~~~~~~~~~~~~~~~~~~
Property values may include variable substitutions. A variable substitution has
one of these forms:

-  `${variable.name}`
-  `${variable.name-default}`

where `variable.name` is the name of a system property. If a system
property by that name exists, the value is substituted. If it does not
exists and a default is specified, the default is substituted after
recursively substituting variables in it. If the system property does
not exist and no default is specified, the original string is not
substituted.

There is also a special variable substitution:

- `read(property.name)[default]`

This will look in the file configured by `boot.properties` for a value. If
there is no `boot.properties` file configured, or the property does not existt,
then the default value is chosen.



Syntax
~~~~~~

The configuration file is line-based, read as UTF-8 encoded, and defined
by the following grammar. `'nl'` is a newline or end of file and
`'text'` is plain text without newlines or the surrounding delimiters
(such as parentheses or square brackets):

.. productionlist::
    configuration: `scala` `app` `repositories` `boot` `log` `appProperties`
    scala: "[" "scala" "]" `nl` `version` `nl` `classifiers` `nl`
    app: "[" "app" "]" `nl` `org` `nl` `name` `nl` `version` `nl` `components` `nl` `class` `nl` `crossVersioned` `nl` `resources` `nl` `classifiers` `nl`
    repositories: "[" "repositories" "]" `nl` (`repository` `nl`)*
    boot: "[" "boot" "]" `nl` `directory` `nl` `bootProperties` `nl` `search` `nl` `promptCreate` `nl` `promptFill` `nl` `quickOption` `nl`
    log: "["' "log" "]" `nl` `logLevel` `nl`
    appProperties: "[" "app-properties" "]" nl (property nl)*
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
