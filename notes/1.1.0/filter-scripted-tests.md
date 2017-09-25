[@jonas]: https://github.com/jonas

[#3564]: https://github.com/sbt/sbt/issues/3564
[#3566]: https://github.com/sbt/sbt/pull/3566

### Improvements

- Filter scripted tests based on optional `project/build.properties`. [#3564]/[#3566] by [@jonas]

### Filtering scripted tests using `project/build.properties`.

For all scripted tests in which `project/build.properties` exist, the value of the `sbt.version` property is read. If its binary version is different from `sbtBinaryVersion in pluginCrossBuild` the test will be skipped and a message indicating this will be logged.

This allows you to define scripted tests that track the minimum supported sbt versions, e.g. 0.13.9 and 1.0.0-RC2.
