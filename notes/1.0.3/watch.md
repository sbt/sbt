### Fixes with compatibility implications

### Improvements

- Add `sbt.watch.mode` system property to allow switching back to old polling behaviour for watch. See below for more details. [#3597][3597] by [@stringbean][@stringbean]

### Bug fixes

#### Alternative watch mode

sbt 1.0.0 introduced a new mechanism for watching for source changes based on the NIO `WatchService` in Java 1.7. On
some platforms (namely macOS) this has led to long delays before changes are picked up. An alternative `WatchService`
for these platforms is planned for sbt 1.1.0 ([#3527][3527]), in the meantime an option to select which watch service
has been added.

The new `sbt.watch.mode` JVM flag has been added with the following supported values:

- `polling`: (default for macOS) poll the filesystem for changes (mechanism used in sbt 0.13).
- `nio` (default for other platforms): use the NIO based `WatchService`.

If you are experiencing long delays on a non-macOS machine then try adding `-Dsbt.watch.mode=polling` to your sbt
options.

[@stringbean]: https://github.com/stringbean
[3527]: https://github.com/sbt/sbt/issues/3527
[3597]: https://github.com/sbt/sbt/pull/3597
