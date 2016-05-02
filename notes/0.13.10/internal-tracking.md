
  [@eed3si9n]: https://github.com/eed3si9n
  [2266]: https://github.com/sbt/sbt/issues/2266
  [2354]: https://github.com/sbt/sbt/pull/2354

### Improvements

- Adds `trackInternalDependencies` and `exportToInternal` keys. See below.

### Inter-project dependency tracking

sbt 0.13.10 adds `trackInternalDependencies` and `exportToInternal` settings. These can be used to control whether to trigger compilation of a dependent subprojects when you call `compile`. Both keys will take one of three values: `TrackLevel.NoTracking`, `TrackLevel.TrackIfMissing`, and `TrackLevel.TrackAlways`. By default they are both set to `TrackLevel.TrackAlways`.

When `trackInternalDependencies` is set to `TrackLevel.TrackIfMissing`, sbt will no longer try to compile internal (inter-project) dependencies automatically, unless there are no `*.class` files (or JAR file when `exportJars` is `true`) in the output directory. When the setting is set to `TrackLevel.NoTracking`, the compilation of internal dependencies will be skipped. Note that the classpath will still be appended, and dependency graph will still show them as dependencies. The motivation is to save the I/O overhead of checking for the changes on a build with many subprojects during development. Here's how to set all subprojects to `TrackIfMissing`.

    lazy val root = (project in file(".")).
      aggregate(....).
      settings(
        inThisBuild(Seq(
          trackInternalDependencies := TrackLevel.TrackIfMissing,
          exportJars := true
        ))
      )

The `exportToInternal` setting allows the dependee subprojects to opt out of the internal tracking, which might be useful if you want to track most subprojects except for a few. The intersection of the `trackInternalDependencies` and `exportToInternal` settings will be used to determine the actual track level. Here's an example to opt-out one project:

    lazy val dontTrackMe = (project in file("dontTrackMe")).
      settings(
        exportToInternal := TrackLevel.NoTracking
      )

[#2266][2266]/[#2354][2354] by [@eed3si9n][@eed3si9n]
