### `update` resolves modules in parallel

Dependency resolution (`update`) no longer holds a process-global lock while
coursier is *not* rendering its interactive progress bar -- that is, whenever a
custom `csrLogger` is set, or coursier is in fallback display mode (the default
under IntelliJ, CI, and any non-TTY environment, or with `COURSIER_PROGRESS=false`).

Previously the lock was taken even for sbt's quiet default logger, so `update`
processed one module at a time and its duration grew with the number of modules
rather than the number of distinct artifacts. Large multi-module builds and
IDE/CI re-imports could spend minutes serializing resolution.

The lock is still held while coursier draws its live per-module progress bars in
an interactive terminal, because that renderer is not safe to drive concurrently;
to parallelize there as well, run with `COURSIER_PROGRESS=false`. Fully parallel
resolution *with* live progress bars is tracked by [#5627][i5627].

This addresses [#5508][i5508].

[i5508]: https://github.com/sbt/sbt/issues/5508
[i5627]: https://github.com/sbt/sbt/issues/5627
