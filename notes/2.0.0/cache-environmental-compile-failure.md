### Environmental compile failures are no longer cached

sbt caches a `CompileFailed` so that an unchanged, still-broken compile does not
re-run from scratch (#7662). But zinc also reports I/O write failures ("error
writing X.class") as compiler problems, so a one-off environmental failure (a
concurrent `target/` deletion, a permission blip) was cached under the same
mechanism and replayed on every later build, even after the cause was gone. When
it hit the metabuild compile the build stayed wedged across restarts, recoverable
only by deleting the global cache by hand. Compile failures whose errors carry no
source position are now treated as environmental and left uncached, so the next
build retries for real. A position-less failure already sitting in a cache
written by an earlier sbt is likewise no longer replayed: the task re-runs and a
success overwrites the stale entry, so previously wedged builds recover on
upgrade without deleting the cache.

This addresses [#9455][i9455].

[i9455]: https://github.com/sbt/sbt/issues/9455
