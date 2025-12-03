# Migration Guide from sbt 1 to sbt 2

In principle, migrating your build from sbt 1 to sbt 2 should be a matter of updating the sbt version in `build.properties`. However, there are some changes in behavior that you may need to account for.

This document will outline possible changes in behavior that affect your build when migrating from sbt 1 to sbt 2. Implement them in case out-of-the-box migration does not work.

## Changes in behavior

- `exportJars` defaults to `true`, was `false`. This might break `getResource("/")` and `resource.toURI`. Set `exportJars := false` if this logic is broken in your build, producing `NullPointerException`s and `FileSystemNotFoundException`s. Set `exportJars := false` in your build if you want to keep the old behavior. The change was introduced by [sbt/sbt#7464](https://github.com/sbt/sbt/pull/7464), see also [blog](https://eed3si9n.com/sbt-remote-cache/).
