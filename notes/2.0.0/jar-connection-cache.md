### Stale test-resource reads after rebuilding the tests jar

With `exportJars := true` (the 2.x default) test resources are served from the
tests jar. The JVM caches open jar files per URL for `jar:` connections, so
after a resource edit rebuilt the jar in place, a warm sbt session's next test
run could read the old resource bytes through `getResourceAsStream` while class
files stayed fresh: the test re-ran (digest invalidation works) but wrongly
passed against stale content, and that wrong result was then cached under the
new digest. sbt has long disabled this JVM cache for its own scripted tests "to
avoid interference between tests"; it now disables it for every session.

This addresses [#9468][i9468].

[i9468]: https://github.com/sbt/sbt/issues/9468
