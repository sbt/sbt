## Dependency tree: duplicate-subtree collapse

`dependencyTree`, `dependencyBrowseTree`, and `inspect tree` now collapse
duplicate subtrees in a DAG to a single line marked `(*)`, matching the
convention used by Maven's `dependency:tree`. The first occurrence is
rendered in full; subsequent occurrences appear as `+- <id> (*)`.

This fixes [#6886][i6886]: rendering a deep diamond DAG no longer
produces `O(M^N)` output (and the OOMs that came with it).

### Output change

Before:

```
o:root_2.13:0.1
  +-o:subA_2.13:0.1 [S]
  | +-o:common_2.13:0.1 [S]
  | | +-org.scala-lang:scala-library:2.13.16 [S]
  | +-org.scala-lang:scala-library:2.13.16 [S]
  +-o:subB_2.13:0.1 [S]
  | +-o:common_2.13:0.1 [S]                          # full subtree again
  | | +-org.scala-lang:scala-library:2.13.16 [S]
  | +-org.scala-lang:scala-library:2.13.16 [S]
  +-org.scala-lang:scala-library:2.13.16 [S]
```

After:

```
o:root_2.13:0.1
  +-o:subA_2.13:0.1 [S]
  | +-o:common_2.13:0.1 [S]
  | | +-org.scala-lang:scala-library:2.13.16 [S]
  | +-org.scala-lang:scala-library:2.13.16 [S]
  +-o:subB_2.13:0.1 [S]
  | +-o:common_2.13:0.1 [S] (*)                      # collapsed
  | +-org.scala-lang:scala-library:2.13.16 [S] (*)
  +-org.scala-lang:scala-library:2.13.16 [S] (*)
```

### Affected surfaces

- `dependencyTree`: ASCII output (also `dependencyTreeList`,
  `dependencyTreeStats`).
- `dependencyBrowseTree`: JSON / HTML view.
- `inspect tree`: the setting-graph renderer is the same code path, so
  the `(*)` marker shows up there too. Most users don't think of
  `inspect tree` as "the dependency tree" -- this note is the
  heads-up.

### Contract for tooling consumers

A line whose entry ends with `(*)` is a reference to the canonical
(first-rendered) occurrence of that node within the same render.
Tools parsing `dependencyTree` / `dependencyBrowseTree` output should
treat `<id> (*)` as a back-pointer rather than a distinct dependency.

### Scope

Dedup is currently within a single root's subtree. Cross-root dedup
(when a `ModuleGraph` has multiple roots that share a transitive
closure) is tracked separately as [#9227][i9227].

[i6886]: https://github.com/sbt/sbt/issues/6886
[i9227]: https://github.com/sbt/sbt/issues/9227
