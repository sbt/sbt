# Java Sources

sbt has support for compiling Java sources with the limitation that dependency tracking is limited to the dependencies present in compiled class files.

# Usage

* `compile` will compile the sources under `src/main/java` by default.
* `test-compile` will compile the sources under `src/test/java` by default.

Pass options to the Java compiler by setting `javac-options`:

```scala
javacOptions += "-g:none"
```

As with options for the Scala compiler, the arguments are not parsed by sbt.  Multi-element options, such as `-source 1.5`, are specified like:

```scala
javacOptions ++= Seq("-source", "1.5")
```

You can specify the order in which Scala and Java sources are built with the `compile-order` setting.  Possible values are from the `CompileOrder` enumeration: `Mixed`, `JavaThenScala`, and `ScalaThenJava`.  If you have circular dependencies between Scala and Java sources, you need the default, `Mixed`, which passes both Java and Scala sources to `scalac` and then compiles the Java sources with `javac`.  If you do not have circular dependencies, you can use one of the other two options to speed up your build by not passing the Java sources to `scalac`.  For example, if your Scala sources depend on your Java sources, but your Java sources do not depend on your Scala sources, you can do:

```scala
compileOrder := CompileOrder.JavaThenScala
```

To specify different orders for main and test sources, scope the setting by configuration:

```scala
// Java then Scala for main sources
compileOrder in Compile := CompileOrder.JavaThenScala

// allow circular dependencies for test sources
compileOrder in Test := CompileOrder.Mixed
```

Note that in an incremental compilation setting, it is not practical to ensure complete isolation between Java sources and Scala sources because they share the same output directory.  So, previously compiled classes not involved in the current recompilation may be picked up.  A clean compile will always provide full checking, however.

By default, sbt includes `src/main/scala` and `src/main/java` in its list of unmanaged source directories.  For Java-only projects, the unnecessary Scala directories can be ignored by modifying `unmanagedSourceDirectories`:

```scala
// Include only src/main/java in the compile configuration
unmanagedSourceDirectories in Compile <<= Seq(javaSource in Compile).join

// Include only src/test/java in the test configuration
unmanagedSourceDirectories in Test <<= Seq(javaSource in Test).join
```

However, there should not be any harm in leaving the Scala directories if they are empty.