# Coursier

*Pure Scala Artifact Fetching*

A Scala library to fetch dependencies from Maven / Ivy repositories

[![Build Status](https://travis-ci.org/alexarchambault/coursier.svg?branch=master)](https://travis-ci.org/alexarchambault/coursier)
[![Build status (Windows)](https://ci.appveyor.com/api/projects/status/trtum5b7washfbj9?svg=true)](https://ci.appveyor.com/project/alexarchambault/coursier)
[![Join the chat at https://gitter.im/alexarchambault/coursier](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/alexarchambault/coursier?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)
[![Maven Central](https://img.shields.io/maven-central/v/io.get-coursier/coursier_2.11.svg)](https://maven-badges.herokuapp.com/maven-central/io.get-coursier/coursier_2.11)
[![Scaladoc](http://javadoc-badge.appspot.com/io.get-coursier/coursier_2.11.svg?label=scaladoc)](http://javadoc-badge.appspot.com/io.get-coursier/coursier_2.11)

![Demo (courtesy of @paulp)](http://i.imgur.com/lCJ9oql.gif)

*coursier* is a dependency resolver / fetcher *à la* Maven / Ivy, entirely
rewritten from scratch in Scala. It aims at being fast and easy to embed
in other contexts. Its very core (`core` module) aims at being
extremely pure, and only requires to be fed external data (Ivy / Maven metadata) via a monad.

The `cache` module handles caching of the metadata and artifacts themselves,
and is less so pure than the `core` module, in the sense that it happily
does IO as a side-effect (always wrapped in `Task`, and naturally favoring immutability for all
that's kept in memory).

It handles fancy Maven features like
* [POM inheritance](http://books.sonatype.com/mvnref-book/reference/pom-relationships-sect-project-relationships.html#pom-relationships-sect-project-inheritance),
* [dependency management](http://books.sonatype.com/mvnex-book/reference/optimizing-sect-dependencies.html),
* [import scope](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies),
* [properties](http://books.sonatype.com/mvnref-book/reference/resource-filtering-sect-properties.html),
* etc.

and is able to fetch metadata and artifacts from both Maven and Ivy repositories.

Compared to the default dependency resolution of SBT, it adds:
* downloading of artifacts in parallel,
* better offline mode - one can safely work with snapshot dependencies if these are in cache (SBT tends to try and fail if it cannot check for updates),
* non obfuscated cache (cache structure just mimics the URL it caches),
* no global lock (no "Waiting for ~/.ivy2/.sbt.ivy.lock to be available").

From the command-line, it also has:
* a [launcher](#launch), able to launch apps distributed via Maven / Ivy repositories,
* a [bootstrap](#bootstrap) generator, able to generate stripped launchers of these apps.

Lastly, it can be used programmatically via its [API](#api) and has a Scala JS [demo](#scala-js-demo).

## Table of content

1. [Quick start](#quick-start)
  1. [SBT plugin](#sbt-plugin)
  2. [Command-line](#command-line)
  3. [API](#api)
2. [Why](#why)
3. [Usage](#usage)
  1. [SBT plugin](#sbt-plugin-1)
  2. [Command-line](#command-line-1)
  3. [API](#api-1)
  4. [Scala JS demo](#scala-js-demo)
4. [Extra features](#extra-features)
  1. [Printing trees](#printing-trees)
  2. [Generating bootstrap launchers](#generating-bootstrap-launchers)
  3. [Credentials](#credentials)
  4. [Extra protocols](#extra-protocols)
5. [Limitations](#limitations)
6. [FAQ](#faq)
7. [Roadmap](#roadmap)
8. [Development tips](#development-tips)
9. [Contributors](#contributors)
10. [Projects using coursier](#projects-using-coursier)

## Quick start

### SBT plugin

Enable the SBT plugin by adding
```scala
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M14")
```
to `~/.sbt/0.13/plugins/build.sbt` (enables it globally), or to the `project/plugins.sbt` file
of a SBT project. Tested with SBT 0.13.8 / 0.13.9 / 0.13.11 / 0.13.12.


### Command-line

Download and run its launcher with
```
$ curl -L -o coursier https://git.io/vgvpD && chmod +x coursier && ./coursier --help
```

Alternatively on OS X, install it via [@paulp](https://github.com/paulp/)'s homebrew formula,
```
$ brew install --HEAD paulp/extras/coursier
```

Run an application distributed via artifacts with
```
$ ./coursier launch com.lihaoyi:ammonite_2.11.8:0.7.0
```

Download and list the classpath of one or several dependencies with
```
$ ./coursier fetch org.apache.spark:spark-sql_2.11:1.6.1 com.twitter:algebird-spark_2.11:0.12.0
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/com/twitter/algebird-spark_2.11/0.12.0/algebird-spark_2.11-0.12.0.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/com/twitter/algebird-core_2.11/0.12.0/algebird-core_2.11-0.12.0.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/apache/hadoop/hadoop-annotations/2.2.0/hadoop-annotations-2.2.0.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/tukaani/xz/1.0/xz-1.0.jar
...
```

### API

Add to your `build.sbt`
```scala
libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier" % "1.0.0-M14",
  "io.get-coursier" %% "coursier-cache" % "1.0.0-M14"
)
```

Add an import for coursier,
```scala
import coursier._
```







To resolve dependencies, first create a `Resolution` case class with your dependencies in it,
```scala
val start = Resolution(
  Set(
    Dependency(
      Module("org.scalaz", "scalaz-core_2.11"), "7.2.3"
    ),
    Dependency(
      Module("org.typelevel", "cats-core_2.11"), "0.6.0"
    )
  )
)
```

Create a fetch function able to get things from a few repositories via a local cache,
```scala
val repositories = Seq(
  Cache.ivy2Local,
  MavenRepository("https://repo1.maven.org/maven2")
)

val fetch = Fetch.from(repositories, Cache.fetch())
```

Then run the resolution per-se,
```scala
val resolution = start.process.run(fetch).run
```
That will fetch and use metadata.

Check for errors in
```scala
val errors: Seq[(Dependency, Seq[String])] = resolution.errors
```
These would mean that the resolution wasn't able to get metadata about some dependencies.

Then fetch and get local copies of the artifacts themselves (the JARs) with
```scala
import java.io.File
import scalaz.\/
import scalaz.concurrent.Task

val localArtifacts: Seq[FileError \/ File] = Task.gatherUnordered(
  resolution.artifacts.map(Cache.file(_).run)
).run
```


The default global cache used by coursier is `~/.coursier/cache/v1`. E.g. the artifact at
`https://repo1.maven.org/maven2/org/scala-lang/scala-library/2.11.8/scala-library-2.11.8.jar`
will land in `~/.coursier/cache/v1/https/repo1.maven.org/maven2/org/scala-lang/scala-library/2.11.8/scala-library-2.11.8.jar`.

From the SBT plugin, the default repositories are the ones provided by SBT (typically Central or JFrog, and `~/.ivy2/local`).
From the CLI tools, these are Central (`https://repo1.maven.org/maven2`) and `~/.ivy2/local`.
From the API, these are specified manually - you are encouraged to use those too.


## Why

The current state of dependency management in Scala suffers several flaws, that prevent applications to fully
profit from and rely on dependency management. Coursier aims at addressing these by making it easy to:
- resolve / download dependencies programmatically,
- launch applications distributed via Maven / Ivy artifacts from the command-line,
- work offline with artifacts,
- sandbox dependency management between projects.

As its [API](#api) illustrates, getting artifacts of dependencies is just a matter of specifying these along
with a few repositories. You can then straightforwardly get the corresponding artifacts, easily getting
precise feedback about what goes on during the resolution.

Launching an application distributed via Maven artifacts is just a command away with the [launcher](#command-line) of coursier.
In most cases, just specifying the corresponding main dependency is enough to launch the corresponding application.

If all your dependencies are in cache, chances are coursier will not even try to connect to remote repositories. This
also applies to snapshot dependencies of course - these are only updated on demand, not getting constantly in your way
like is currently the case by default with SBT.

When using coursier from the command-line or via its SBT plugin, sandboxing is just one command away. Just do
`export COURSIER_CACHE="$(pwd)/.coursier-cache"`, and the cache will become `.coursier-cache` from the current
directory instead of the default global `~/.coursier/cache/v1`. This allows for example to quickly inspect the content
of the cache used by a particular project, in case you have any doubt about what's in it.

## Usage

### SBT plugin

Enable the SBT plugin globally by adding
```scala
addSbtPlugin("io.get-coursier" % "sbt-coursier" % "1.0.0-M14")
```
to `~/.sbt/0.13/plugins/build.sbt`

To enable it on a per-project basis, add it only to the `project/plugins.sbt` of a SBT project.
The SBT plugin has been tested only with SBT 0.13.8 / 0.13.9 / 0.13.11 / 0.13.12. It doesn't currently work with the SBT 1.0 milestones.

Once enabled, the `update`, `updateClassifiers`, and `updateSbtClassifiers` commands are taken care of by coursier. These
provide more output about what's going on than their default implementations do.




### Command-line

Download and run its launcher with
```
$ curl -L -o coursier https://git.io/vgvpD && chmod +x coursier && ./coursier --help
```

The launcher itself weighs only 8 kB and can be easily embedded as is in other projects.
It downloads the artifacts required to launch coursier on the first run.

Alternatively on OS X, install it via [@paulp](https://github.com/paulp/)'s homebrew formula, that puts the `coursier` launcher directly in your PATH,
```
$ brew install --HEAD paulp/extras/coursier
```

```
$ ./coursier --help
```
lists the available coursier commands. The most notable ones are `launch`, and `fetch`. Type
```
$ ./coursier command --help
```
to get a description of the various options the command `command` (replace with one
of the above command) accepts.

Both commands below can be given repositories with the `-r` or `--repository` option, like
```
-r central
-r https://oss.sonatype.org/content/repositories/snapshots
-r "ivy:https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]"
```

`central` and `ivy2local` correspond to Maven Central and `~/.ivy2/local`. These are used by default
unless the `--no-default` option is specified.

Repositories starting with `ivy:` are assumed to be Ivy repositories, specified with an Ivy pattern, like `ivy:https://repo.typesafe.com/typesafe/ivy-releases/[organisation]/[module]/(scala_[scalaVersion]/)(sbt_[sbtVersion]/)[revision]/[type]s/[artifact](-[classifier]).[ext]`.
Else, a Maven repository is assumed.

To set credentials for a repository, pass a user and password in its URL, like
```
-r https://user:pass@nexus.corp.com/content/repositories/releases
```

#### launch

The `launch` command fetches a set of Maven coordinates it is given, along
with their transitive dependencies, then launches the "main `main` class" from
it if it can find one (typically from the manifest of the first coordinates).
The main class to launch can also be manually specified with the `-M` option.

For example, it can launch:

* [Ammonite](https://github.com/lihaoyi/Ammonite) (enhanced Scala REPL),
```
$ ./coursier launch com.lihaoyi:ammonite_2.11.8:0.7.0
```

along with the REPLs of various JVM languages like

* Frege,
```
$ ./coursier launch -r central -r https://oss.sonatype.org/content/groups/public \
    org.frege-lang:frege-repl-core:1.3 -M frege.repl.FregeRepl
```

* clojure,
```
$ ./coursier launch org.clojure:clojure:1.7.0 -M clojure.main
```

* jruby,
```
$ wget https://raw.githubusercontent.com/jruby/jruby/master/bin/jirb && \
  ./coursier launch org.jruby:jruby:9.0.4.0 -M org.jruby.Main -- -- jirb
```

* jython,
```
$ ./coursier launch org.python:jython-standalone:2.7.0 -M org.python.util.jython
```

* Groovy,
```
$ ./coursier launch org.codehaus.groovy:groovy-groovysh:2.4.5 -M org.codehaus.groovy.tools.shell.Main \
    commons-cli:commons-cli:1.3.1
```

etc.

and various programs, like

* ProGuard and its utility Retrace,
```
$ ./coursier launch net.sf.proguard:proguard-base:5.2.1 -M proguard.ProGuard
$ ./coursier launch net.sf.proguard:proguard-retrace:5.2.1 -M proguard.retrace.ReTrace
```

* Wiremock,
```
./coursier launch com.github.tomakehurst:wiremock:1.57 -- \
--proxy-all="http://search.twitter.com" --record-mappings --verbose
```

If you wish to pass additional argument to the artifact being launched, separate them from the coursier's parameters list with the "--", just like in the Wiremock example above.

#### fetch

The `fetch` command simply fetches a set of dependencies, along with their
transitive dependencies, then prints the local paths of all their artifacts.

Example
```
$ ./coursier fetch org.apache.spark:spark-sql_2.11:1.6.1
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/apache/hadoop/hadoop-annotations/2.2.0/hadoop-annotations-2.2.0.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/tukaani/xz/1.0/xz-1.0.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/tachyonproject/tachyon-underfs-s3/0.8.2/tachyon-underfs-s3-0.8.2.jar
/path/to/.coursier/cache/v1/https/repo1.maven.org/maven2/org/glassfish/grizzly/grizzly-http/2.1.2/grizzly-http-2.1.2.jar
...
```

By adding the `-p` option, these paths can be handed over directly to
`java -cp`, like
```
$ java -cp "$(./coursier fetch -p com.lihaoyi:ammonite_2.11.8:0.7.0)" ammonite.Main
Loading...
Welcome to the Ammonite Repl 0.7.0
(Scala 2.11.8 Java 1.8.0_60)
@
```





### bootstrap

The `bootstrap` generates tiny bootstrap launchers, able to pull their dependencies from
repositories on first launch. For example, the launcher of coursier is [generated](https://github.com/alexarchambault/coursier/blob/master/project/generate-launcher.sh) with a command like
```
$ ./coursier bootstrap \
    io.get-coursier:coursier-cli_2.11:1.0.0-M14 \
    -b -f -o coursier \
    -M coursier.cli.Coursier
```

See `./coursier bootstrap --help` for a list of the available options.

### API

Add to your `build.sbt`
```scala
libraryDependencies ++= Seq(
  "io.get-coursier" %% "coursier" % "1.0.0-M14",
  "io.get-coursier" %% "coursier-cache" % "1.0.0-M14"
)
```

The first module, `"io.get-coursier" %% "coursier" % "1.0.0-M14"`, mainly depends on
`scalaz-core` (and only it, *not* `scalaz-concurrent` for example). It contains among others,
definitions,
mainly in [`Definitions.scala`](https://github.com/alexarchambault/coursier/blob/master/core/shared/src/main/scala/coursier/core/Definitions.scala),
[`Resolution`](https://github.com/alexarchambault/coursier/blob/master/core/shared/src/main/scala/coursier/core/Resolution.scala), representing a particular state of the resolution,
and [`ResolutionProcess`](https://github.com/alexarchambault/coursier/blob/master/core/shared/src/main/scala/coursier/core/ResolutionProcess.scala),
that expects to be given metadata, wrapped in any `Monad`, then feeds these to `Resolution`, and at the end gives
you the final `Resolution`, wrapped in the same `Monad` it was given input. This final `Resolution` has all the dependencies,
including the transitive ones.

The second module, `"io.get-coursier" %% "coursier-cache" % "1.0.0-M14"`, is precisely in charge of fetching
these input metadata. It uses `scalaz.concurrent.Task` as a `Monad` to wrap them. It also fetches artifacts (JARs, etc.).
It caches all of these (metadata and artifacts) on disk, and validates checksums too.

In the code below, we'll assume some imports are around,
```scala
import coursier._
```





Resolving dependencies involves create an initial resolution state, with all the initial dependencies in it, like
```scala
val start = Resolution(
  Set(
    Dependency(
      Module("org.typelevel", "cats-core_2.11"), "0.6.0"
    ),
    Dependency(
      Module("org.scalaz", "scalaz-core_2.11"), "7.2.3"
    )
  )
)
```

It goes without saying that a `Resolution` is immutable, as are all the classes defined in the core module.
The resolution process will go on by giving successive `Resolution`s, until the final one.

`start` above is only the initial state - it is far from over, as the `isDone` method on it tells,
```scala
scala> start.isDone
res4: Boolean = false
```




In order for the resolution to go on, we'll need things from a few repositories,
```scala
scala> val repositories = Seq(
     |   Cache.ivy2Local,
     |   MavenRepository("https://repo1.maven.org/maven2")
     | )
repositories: Seq[coursier.core.Repository] = List(IvyRepository(Pattern(List(Const(file://), Var(user.home), Const(/local/), Var(organisation), Const(/), Var(module), Const(/), Opt(WrappedArray(Const(scala_), Var(scalaVersion), Const(/))), Opt(WrappedArray(Const(sbt_), Var(sbtVersion), Const(/))), Var(revision), Const(/), Var(type), Const(s/), Var(artifact), Opt(WrappedArray(Const(-), Var(classifier))), Const(.), Var(ext))),None,None,true,true,true,true,None), MavenRepository(https://repo1.maven.org/maven2,None,false,None,Set(org.apache.zookeeper:zookeeper)))
```
The first one, `Cache.ivy2Local`, is defined in `coursier.Cache`, itself from the `coursier-cache` module that
we added above. As we can see, it is an `IvyRepository`, picking things under `~/.ivy2/local`. An `IvyRepository`
is related to the [Ivy](http://ant.apache.org/ivy/) build tool. This kind of repository involves a so-called [pattern](http://ant.apache.org/ivy/history/2.4.0/concept.html#patterns), with
various properties. These are not of very common use in Scala, although SBT uses them a bit.

The second repository is a `MavenRepository`. These are simpler than the Ivy repositories. They're the ones
we're the most used to in Scala. Common ones like [Central](https://repo1.maven.org/maven2) like here, or the repositories
from [Sonatype](https://oss.sonatype.org/content/repositories/), are Maven repositories. These originate
from the [Maven](https://maven.apache.org/) build tool. Unlike the Ivy repositories which involve customisable patterns to point
to the underlying metadata and artifacts, the paths of these for Maven repositories all look alike,
like for any particular version of the standard library, under paths like
[this one](http://repo1.maven.org/maven2/org/scala-lang/scala-library/2.11.7/).

Both `IvyRepository` and `MavenRepository` are case classes, so that it's straightforward to specify one's own
repositories.

To set credentials for a `MavenRepository` or `IvyRepository`, set their `authentication` field, like
```scala
scala> import coursier.core.Authentication
import coursier.core.Authentication

scala> MavenRepository(
     |   "https://nexus.corp.com/content/repositories/releases",
     |   authentication = Some(Authentication("user", "pass"))
     | )
res6: coursier.maven.MavenRepository = MavenRepository(https://nexus.corp.com/content/repositories/releases,None,false,Some(Authentication(user, *******)),Set(org.apache.zookeeper:zookeeper))
```

Now that we have repositories, we're going to mix these with things from the `coursier-cache` module,
for resolution to happen via the cache. We'll create a function
of type `Seq[(Module, String)] => F[Seq[((Module, String), Seq[String] \/ (Artifact.Source, Project))]]`.
Given a sequence of dependencies, designated by their `Module` (organisation and name in most cases)
and version (just a `String`), it gives either errors (`Seq[String]`) or metadata (`(Artifact.Source, Project)`),
wrapping the whole in a monad `F`.
```scala
val fetch = Fetch.from(repositories, Cache.fetch())
```

The monad used by `Fetch.from` is `scalaz.concurrent.Task`, but the resolution process is not tied to a particular
monad - any stack-safe monad would do.

With this `fetch` method, we can now go on with the resolution. Calling `process` on `start` above gives a
[`ResolutionProcess`](https://github.com/alexarchambault/coursier/blob/master/core/shared/src/main/scala/coursier/core/ResolutionProcess.scala),
that drives the resolution. It is loosely inspired by the `Process` of scalaz-stream.
It is an immutable structure, that represents the various states the resolution process can be in.

Its method `current` gives the current `Resolution`. Calling `isDone` on the latter says whether the
resolution is done or not.

The `next` method, that expects a `fetch` method like the one above, gives
the "next" state of the resolution process, wrapped in the monad of the `fetch` method. It allows to do
one resolution step.

Lastly, the `run` method runs the whole resolution until its end. It expects a `fetch` method too,
and will make at most `maxIterations` steps (50 by default), and return the "final" resolution state,
wrapped in the monad of `fetch`. One should check that the `Resolution` it returns is done (`isDone`) -
the contrary means that `maxIterations` were reached, likely signaling an issue, unless the underlying
resolution is particularly complex, in which case `maxIterations` could be increased.

Let's run the whole resolution,
```scala
val resolution = start.process.run(fetch).run
```

To get additional feedback during the resolution, we can give the `Cache.default` method above
a [`Cache.Logger`](https://github.com/alexarchambault/coursier/blob/cf269c6895e19f2d590f08811406724304332950/cache/src/main/scala/coursier/Cache.scala#L484-L490).

By default, downloads happen in a global fixed thread pool (with 6 threads, allowing for 6 parallel downloads), but
you can supply your own thread pool to `Cache.default`.

Now that the resolution is done, we can check for errors in
```scala
val errors: Seq[(Dependency, Seq[String])] = resolution.errors
```
These would mean that the resolution wasn't able to get metadata about some dependencies.

We can also check for version conflicts, in
```scala
val conflicts: Set[Dependency] = resolution.conflicts
```
which are dependencies whose versions could not be unified.

Then, if all went well, we can fetch and get local copies of the artifacts themselves (the JARs) with
```scala
import java.io.File
import scalaz.\/
import scalaz.concurrent.Task

val localArtifacts: Seq[FileError \/ File] = Task.gatherUnordered(
  resolution.artifacts.map(Cache.file(_).run)
).run
```

We're using the `Cache.file` method, that can also be given a `Logger` (for more feedback) and a custom thread pool.


### Scala JS demo

*coursier* is also compiled to Scala JS, and can be tested in the browser via its
[demo](http://alexarchambault.github.io/coursier/#demo).

## Extra features

### TTL

Changing things in cache are given a time-to-live (TTL) of **24 hours** by default. Changing things are artifacts for versions ending with `-SNAPSHOT`, Maven metadata files listing available versions, etc.

The most straightforward way of changing that consists in setting `COURSIER_TTL` in the environment. It's parsed with `scala.concurrent.duration.Duration`, so that things like `24 hours`, `5 min`, `10s`, or `0s`, are fine, and it accepts infinity (`Inf`) as a duration.




### Printing trees

E.g. to print the dependency tree of `io.circe:circe-core:0.4.1`,
```
$ coursier resolve -t io.circe:circe-core_2.11:0.4.1
  Result:
└─ io.circe:circe-core_2.11:0.4.1
   ├─ io.circe:circe-numbers_2.11:0.4.1
   |  └─ org.scala-lang:scala-library:2.11.8
   ├─ org.scala-lang:scala-library:2.11.8
   └─ org.typelevel:cats-core_2.11:0.4.1
      ├─ com.github.mpilquist:simulacrum_2.11:0.7.0
      |  ├─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
      |  └─ org.typelevel:macro-compat_2.11:1.1.0
      |     └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
...
```

From SBT, with sbt-coursier enabled, the command `coursierDependencyTree` prints the dependency tree of the various sub-projects,
```
> coursierDependencyTree
io.get-coursier:coursier_2.11:1.0.0-SNAPSHOT
├─ com.lihaoyi:fastparse_2.11:0.3.7
|  ├─ com.lihaoyi:fastparse-utils_2.11:0.3.7
|  |  ├─ com.lihaoyi:sourcecode_2.11:0.1.1
|  |  |  └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
|  |  └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
|  ├─ com.lihaoyi:sourcecode_2.11:0.1.1
|  |  └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
|  └─ org.scala-lang:scala-library:2.11.7 -> 2.11.8
├─ org.jsoup:jsoup:1.9.2
...
```

Note that this command can be scoped to sub-projects, like `proj/coursierDependencyTree`.

The printed trees highlight version bumps, that only change the patch number, in yellow. The `2.11.7 -> 2.11.8` above mean that the parent dependency wanted version `2.11.7`, but version `2.11.8` landed in the classpath, pulled in this version by other dependencies.

They highlight in red version bumps that may not be binary compatible, changing major or minor version number.

### Generating bootstrap launchers

The `coursier bootstrap` command generates tiny bootstrap launchers (~12 kB). These are able to download their dependencies upon first launch, then launch the corresponding application. E.g. to generate a launcher for scalafmt,
```
$ coursier bootstrap com.geirsson:scalafmt-cli_2.11:0.2.3 -o scalafmt
```

This generates a `scalafmt` file, which is a tiny JAR, corresponding to the `bootstrap` sub-project of coursier. It contains resource files, with the URLs of the various dependencies of scalafmt. On first launch, these are downloaded under `~/.coursier/bootstrap/com.geirsson/scalafmt-cli_2.11` (following the organization and name of the first dependency - note that this directory can be changed with the `-D` option). Nothing needs to be downloaded once all the dependencies are there, and the application is then launched straightaway.

### Credentials

To use artifacts from repositories requiring credentials, pass the user and password via the repository URL, like
```
$ coursier fetch -r https://user:pass@company.com/repo com.company:lib:0.1.0
```

From SBT, add the setting `coursierUseSbtCredentials := true` for sbt-coursier to use the credentials set via the `credentials` key. This manual step was added in order for the `credentials` setting not to be checked if not needed, as it seems to acquire some (good ol') global lock when checked, which sbt-coursier aims at avoiding.

### Extra protocols

By default, coursier and sbt-coursier handle the `http://`, `https://`, and `file://` protocols. It should also be fine
by protocols supported by `java.net.URL` (not thoroughly tested). Support for other protocols can be added via plugins. [coursier-s3](https://github.com/rtfpessoa/coursier-s3), a plugin for S3, is under development, and illustrates how to write such plugins.

## Limitations

#### Ivy support is poorly tested

The minimum was made for SBT plugins to be resolved fine (including dependencies
between plugins, the possibility that some of them come from Maven repositories,
with a peculiarities, classifiers - sources, javadoc - should be fine too).
So it is likely that projects relying more heavily
on Ivy features could run into the limitations of the current implementation.

Any issue report related to that, illustrated with public Ivy repositories
if possible, would be greatly appreciated.

#### *Important*: SBT plugin might mess with published artifacts

SBT seems to require the `update` command to generate a few metadata files
later used by `publish`. If ever there's an issue with these, this might
add discrepancies in the artifacts published with `publish` or `publishLocal`.
Should you want to use the coursier SBT plugin while publishing artifacts at the
same time, I'd recommend an extreme caution at first, like manually inspecting
the metadata files and compare with previous ones, to ensure everything's fine.

coursier publishes its artifacts with its own plugin enabled since version
`1.0.0-M2` though, without any apparent problem.

#### No wait on locked file

If ever resolution or artifact downloading stumbles upon a locked metadata or
artifact in the cache, it will just fail, instead of waiting for the lock to be freed.

#### Also

Plus the inherent amount of bugs arising in a young project :-)

## FAQ

#### Even though the coursier SBT plugin is enabled and some `coursier*` keys can be found from the SBT prompt, dependency resolution seems still to be handled by SBT itself. Why?

Check that the default SBT settings (`sbt.Defaults.defaultSettings`) are not manually added to your project.
These define commands that the coursier SBT plugin overrides. Adding them again erases these overrides,
effectively disabling coursier.

#### With spark >= 1.5, I get some `NoVerifyError` exceptions related to jboss/netty. Why?

This error originates from the `org.jboss.netty:netty:3.2.2.Final` dependency to be put in the classpath.
Exclude it from your spark dependencies with the exclusion `org.jboss.netty:netty`.

Coursier tries to follow the Maven documentation to build the full dependency set, in particular
some [points about dependency exclusion](https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html#Dependency_Exclusions).
Inspecting the `org.apache.spark:spark-core_2.11:1.5.2` dependency graph shows that spark-core
depends on `org.jboss.netty:netty:3.2.2.Final` via the following path: `org.apache.spark:spark-core_2.11:1.5.2` ->
`org.tachyonproject:tachyon-client:0.7.1` -> `org.apache.curator:curator-framework:2.4.0` ->
`org.apache.zookeeper:zookeeper:3.4.5` -> `org.jboss.netty:netty:3.2.2.Final`. Even though
spark-core tries to exclude `org.jboss.netty:netty` to land in its classpath via some other dependencies
(e.g. it excludes it via its dependencies towards `org.apache.hadoop:hadoop-client` and `org.apache.curator:curator-recipes`),
it does not via the former path. So it depends on it according to the
[Maven documentation](https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html#Dependency_Exclusions).

This is likely unintended, as it leads to exceptions like
```
java.lang.VerifyError: (class: org/jboss/netty/channel/socket/nio/NioWorkerPool, method: createWorker signature: (Ljava/util/concurrent/Executor;)Lorg/jboss/netty/channel/socket/nio/AbstractNioWorker;) Wrong return type in function
```
Excluding `org.jboss.netty:netty` from the spark dependencies fixes it.

#### On first launch, the coursier launcher downloads a 1.5+ MB JAR. Is it possible to have a standalone launcher, that would not need to download things on first launch?

Run `project/generate-launcher.sh -s` from the root of the coursier sources. That will generate a new (bigger) `coursier` launcher, that needs not to download anything on first launch.

#### How can the launcher be run on Windows, or manually with the `java` program?

Download it from the same link as the command above. Then run from a console, in the directory where the `coursier` launcher is,
```
> java -noverify -jar coursier
```
The `-noverify` option seems to be required after the proguarding step of the main JAR of coursier.

#### How to enable sandboxing?

Set the `COURSIER_CACHE` prior to running `coursier` or SBT, like
```
$ COURSIER_CACHE=$(pwd)/.coursier-cache coursier
```
or
```
$ COURSIER_CACHE=$(pwd)/.coursier-cache sbt
```

## Development tips

In general, as coursier has a few modules that target either only scala 2.10 or 2.11, it is recommended
to systematically force the scala version, with the `++2.11.8` or `++2.10.6` commands. The `cli` module
in particular is only built in 2.11, and the `plugin` one only in 2.10.

#### Working on the plugin module in an IDE

Set `scalaVersion` to `2.10.6` in `build.sbt`. Then re-open / reload the coursier project.

#### Running the Scala JS tests

They require `npm install` to have been run once from the `coursier` directory or a subdirectory of
it. They can then be run with `sbt testsJS/test`.

#### Quickly running the CLI app from the sources

Run
```
$ sbt "~cli/pack"
```

This generates and updates a runnable distribution of coursier in `target/pack`, via
the [sbt-pack](https://github.com/xerial/sbt-pack/) plugin.

It can be run from another terminal with
```
$ cli/target/pack/bin/coursier
```

## Roadmap

The first releases were milestones like `0.1.0-M?`. As a launcher, basic Ivy
repositories support, and an SBT plugin, were added in the mean time,
coursier is now aiming directly at `1.0.0`.

The last features I'd like to add until a feature freeze are mainly a
better / nicer output, for both the command-line tools and the SBT plugin.
These are tracked via GitHub [issues](https://github.com/alexarchambault/coursier/issues?q=is%3Aopen+is%3Aissue+milestone%3A1.0.0), along with other points.
Milestones will keep being released until then.
Then coursier should undergo `RC` releases, with no new features added, and
only fixes and minor refactorings between them.
Once RCs will be considered stable enough, `1.0.0` should be released.

## Contributors

- Erik LaBianca ([@easel](https://github.com/easel))
- Han Ju ([@darkjh](https://github.com/darkjh))
- Jameel Al-Aziz ([@jalaziz](https://github.com/jalaziz))
- joriscode ([@joriscode](https://github.com/joriscode))
- Rodrigo Fernandes ([@rtfpessoa](https://github.com/rtfpessoa))
- Roman Iakovlev ([@RomanIakovlev](https://github.com/RomanIakovlev))
- Simon Ochsenreither ([@soc](https://github.com/soc))
- Your name here :-)

Don't hesitate to pick an issue to contribute, and / or ask for help for how to proceed
on the [Gitter channel](https://gitter.im/alexarchambault/coursier).

## Projects using coursier

- [Lars Hupel](https://github.com/larsrh/)'s [libisabelle](https://github.com/larsrh/libisabelle) fetches
some of its requirements via coursier,
- [jupyter-scala](https://github.com/alexarchambault/jupyter-scala) is launched
and allows to add dependencies in its sessions with coursier (initial motivation
for writing coursier),
- [Apache Toree](https://github.com/apache/incubator-toree) - formerly known as [spark-kernel](https://github.com/ibm-et/spark-kernel), is now using coursier to
add dependencies on-the-fly ([#4](https://github.com/apache/incubator-toree/pull/4)),
- [Quill](https://github.com/getquill/quill) is using coursier for faster dependency resolution ([#591](https://github.com/getquill/quill/pull/591)),
- Your project here :-)


Released under the Apache license, v2.
