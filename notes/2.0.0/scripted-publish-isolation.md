### scripted publishes into repositories of its own

A scripted test that ran `publishLocal` or `publishM2` wrote into the real `~/.ivy2/local` and
`~/.m2/repository`, so a later run could resolve what an earlier run left behind.

Scripted now sends `publishLocal` to `<test dir>/global/local-repo` through the new
`-Dsbt.local_repository` property, and `publishM2` to `<test dir>/global/m2-repo` through
`-Dmaven.repo.local`. The coursier cache and `~/.ivy2/local` stay readable. `mavenLocal` follows
`maven.repo.local`, so a test no longer reads the real `~/.m2`.

A test that checks an absolute `~/.ivy2/local` or `~/.m2` path should read the property instead.
To keep the old target, set the property in `scriptedLaunchOpts`:

```scala
scriptedLaunchOpts += s"-Dsbt.local_repository=${Path.userHome / ".ivy2" / "local"}"
```

The `publishLocal` that `scripted` runs on the host before the tests is not changed. This is
part of [#1361][i1361].

[i1361]: https://github.com/sbt/sbt/issues/1361
