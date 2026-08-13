### Directories declared with `Def.declareOutputDirectory` are restored on cache hits

A directory declared as a cached task's output is packaged as a `.sbtdir.zip`
sibling of the directory. Deleting the directory leaves the sibling zip behind
(an `rm -rf` of the directory or of `classes/` does exactly this), which left
the cache convinced everything was in sync: on the next cache hit the task did not re-run, but the
directory was never re-extracted either. For sbt's own `compile`, whose classes
directory is declared this way, a deleted output directory plus a warm cache
meant `run` failed with `ClassNotFoundException` and no recompile. The cache now
re-extracts a declared directory when the directory itself is missing, at the
cost of a single stat on the warm path.

This addresses the directory-restoration half of [#9462][i9462].

[i9462]: https://github.com/sbt/sbt/issues/9462
