## cacheVersion setting

sbt 2.x caches task results by default. `cacheVersion` provides an escape hatch to invalidate all caches when needed.

### Usage

In `build.sbt`:

```scala
Global / cacheVersion := 1L
```

Or via system property:

```
sbt -Dsbt.cacheversion=1
```

### Details

- Defaults to reading system property `sbt.cacheversion`, or else `0L`
- When `cacheVersion` is `0L`, caching behaves identically to previous versions
- Changing the value invalidates all task caches, forcing recomputation
- The value is incorporated into every cache key via `BuildWideCacheConfiguration`
