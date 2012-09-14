# Compiler Plugin Support

There is some special support for using compiler plugins.  You can set `auto-compiler-plugins` to `true` to enable this functionality.

```scala
autoCompilerPlugins := true
```

To use a compiler plugin, you either put it in your unmanaged library directory (`lib/` by default) or add it as managed dependency in the `plugin` configuration.  `addCompilerPlugin` is a convenience method for specifying `plugin` as the configuration for a dependency:

```scala
addCompilerPlugin("org.scala-tools.sxr" %% "sxr" % "0.2.7")
```

The `compile` and `test-compile` actions will use any compiler plugins found in the `lib` directory or in the `plugin` configuration.  You are responsible for configuring the plugins as necessary.  For example, Scala X-Ray requires the extra option:

```scala
	// declare the main Scala source directory as the base directory
scalacOptions <<= (scalacOptions, scalaSource in Compile) { (options, base) =>
	options :+ ("-Psxr:base-directory:" + base.getAbsolutePath)
}
```

You can still specify compiler plugins manually.  For example:

```scala
scalacOptions += "-Xplugin:<path-to-sxr>/sxr-0.2.7.jar"
```

# Continuations Plugin Example

Support for continuations in Scala 2.8 is implemented as a compiler plugin.  You can use the compiler plugin support for this, as shown here.

```scala
autoCompilerPlugins := true

addCompilerPlugin("org.scala-lang.plugins" % "continuations" % "2.8.1")

scalacOptions += "-P:continuations:enable"
```

# Version-specific Compiler Plugin Example

Adding a version-specific compiler plugin can be done as follows:

```scala
autoCompilerPlugins := true

libraryDependencies <<= (scalaVersion, libraryDependencies) { (ver, deps) =>
    deps :+ compilerPlugin("org.scala-lang.plugins" % "continuations" % ver)
}

scalacOptions += "-P:continuations:enable"
```