  [@cunei]: https://github.com/cunei
  [@eed3si9n]: https://github.com/eed3si9n
  [@gkossakowski]: https://github.com/gkossakowski
  [@jsuereth]: https://github.com/jsuereth

  [1721]: https://github.com/sbt/sbt/issues/1721
  [2014]: https://github.com/sbt/sbt/issues/2014
  [2030]: https://github.com/sbt/sbt/pull/2030

### Fixes with compatibility implications

### Improvements

### Bug fixes

- Fixes memory/performance issue with cached resolution. See below.

### Cached resolution fixes

On a larger dependency graph, the JSON file growing to be 100MB+
with 97% of taken up by *caller* information.
The caller information is not useful once the graph is successfully resolved.
To make the matter worse, these large JSON files were never cleaned up.

sbt 0.13.9 creates a single caller to represent all callers,
which fixes `OutOfMemoryException` seen on some builds.
This generally shrinks the size of JSON, so it should make the IO operations faster.
Dynamic graphs will be rotated with directories named after `yyyy-mm-dd`,
and stale JSON files will be cleaned up after few days.

[#2030][2030]/[#1721][1721]/[#2014][2014] by [@eed3si9n][@eed3si9n]
