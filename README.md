# Coursier

*Pure Scala Artifact Fetching*

A pure Scala substitute for [Aether](http://www.eclipse.org/aether/)

[![Build Status](https://travis-ci.org/alexarchambault/coursier.svg?branch=master)](https://travis-ci.org/alexarchambault/coursier)
[![Build status (Windows)](https://ci.appveyor.com/api/projects/status/trtum5b7washfbj9?svg=true)](https://ci.appveyor.com/project/alexarchambault/coursier)
[![Join the chat at https://gitter.im/alexarchambault/coursier](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/alexarchambault/coursier?utm_source=badge&utm_medium=badge&utm_campaign=pr-badge&utm_content=badge)

*coursier* is a dependency resolver / fetcher *à la* Maven / Ivy, entirely
rewritten from scratch in Scala. It aims at being fast and easy to embed
in other contexts. Its very core (`core` module) aims at being
extremely pure, and should be approached with a mathsy / algebraic mindset.

The `files` module handles caching of the metadata and artifacts themselves,
and is less so pure than the `core` module, in the sense that it happily
does IO as a side-effect (although it naturally favors immutability for all
that's kept in memory).

It handles fancy Maven features like
* [POM inheritance](http://books.sonatype.com/mvnref-book/reference/pom-relationships-sect-project-relationships.html#pom-relationships-sect-project-inheritance),
* [dependency management](http://books.sonatype.com/mvnex-book/reference/optimizing-sect-dependencies.html),
* [import scope](https://maven.apache.org/guides/introduction/introduction-to-dependency-mechanism.html#Importing_Dependencies),
* [properties](http://books.sonatype.com/mvnref-book/reference/resource-filtering-sect-properties.html),
* etc.

It happily resolves dependencies involving modules from the Hadoop ecosystem (Spark, Flink, etc.), that
make a heavy use of these.

It can be used either from the command-line, via its API, or from the browser.

## Command-line

Download and run it with
```
$ curl -L -o coursier https://git.io/vBkT9; chmod +x coursier; ./coursier --help
```

The first time it is run, it will download the artifacts required to launch
coursier. You'll be fine the next times :-).

```
$ ./coursier --help
```
lists the available coursier commands. The most notable ones are `launch`,
`fetch`, and `classpath`. Type
```
$ ./coursier command --help
```
to get a description of the various options the command `command` (replace with one
of the above command) accepts.

### launch

The `launch` command fetches a set of Maven coordinates it is given, along
with their transitive dependencies, then launches the "main `main` class" from
it if it can find one (typically from the manifest of the first coordinates).
The main class to launch can also be manually specified with the `-M` option.

For example, it can launch:

* [Ammonite](https://github.com/lihaoyi/Ammonite) (enhanced Scala REPL),
```
$ ./coursier launch com.lihaoyi:ammonite-repl_2.11.7:0.5.0
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

* Proguard and its utility Retrace,
```
$ ./coursier launch net.sf.proguard:proguard-base:5.2.1 -M proguard.ProGuard
$ ./coursier launch net.sf.proguard:proguard-retrace:5.2.1 -M proguard.retrace.ReTrace
```

### fetch

The `fetch` command simply fetches a set of dependencies, along with their
transitive dependencies, then prints the local paths of all their artefacts.

Example
```
$ ./coursier fetch org.apache.spark:spark-sql_2.11:1.5.2
...
/path/to/.coursier/cache/0.1.0-SNAPSHOT-2f5e731/files/central/io/dropwizard/metrics/metrics-jvm/3.1.2/metrics-jvm-3.1.2.jar
/path/to/.coursier/cache/0.1.0-SNAPSHOT-2f5e731/files/central/javax/servlet/javax.servlet-api/3.0.1/javax.servlet-api-3.0.1.jar
/path/to/.coursier/cache/0.1.0-SNAPSHOT-2f5e731/files/central/javax/inject/javax.inject/1/javax.inject-1.jar
...
```

### classpath

The `classpath` command transitively fetches a set of dependencies like
`fetch` does, then prints a classpath that can be handed over directly
to `java`, like
```
$ java -cp "$(./coursier classpath com.lihaoyi:ammonite-repl_2.11.7:0.5.0 | tail -n1)" ammonite.repl.Repl
Loading...
Welcome to the Ammonite Repl 0.5.0
(Scala 2.11.7 Java 1.8.0_60)
@
```

## API

This [gist](ammonite.repl.Repl) by [Lars Hupel](https://github.com/larsrh/)
illustrates how the API of coursier can be used to get transitives dependencies
and fetch the corresponding artefacts.

More explanations to come :-)

## Scala JS demo

*coursier* is also compiled to Scala JS, and can be tested in the browser via its
[demo](http://alexarchambault.github.io/coursier/#demo).

# To do / missing

- Snapshots metadata / artifacts, once in cache, are not automatically
updated for now. [#41](https://github.com/alexarchambault/coursier/issues/41)
- File locking could be better (none for metadata, no re-attempt if file locked elsewhere for artifacts) [#71](https://github.com/alexarchambault/coursier/issues/71)
- Handle "configurations" like Ivy does, instead of just the standard
(hard-coded) Maven "scopes" [#8](https://github.com/alexarchambault/coursier/issues/8)
- SBT plugin [#52](https://github.com/alexarchambault/coursier/issues/52),
requires Ivy-like configurations [#8](https://github.com/alexarchambault/coursier/issues/8)

See the list of [issues](https://github.com/alexarchambault/coursier/issues).

# Contributors

- Your name here :-)

Don't hesitate to pick an issue to contribute, and / or ask for help for how to proceed
on the [Gitter channel](https://gitter.im/alexarchambault/coursier).

# Projects using coursier

- [Lars Hupel](https://github.com/larsrh/)'s [libisabelle](https://github.com/larsrh/libisabelle) fetches
some of its requirements via coursier,
- [jupyter-scala](https://github.com/alexarchambault/jupyter-scala) should soon allow
to add dependencies in its sessions with coursier (initial motivation for writing coursier),
- Your project here :-)


Released under the Apache license, v2.
