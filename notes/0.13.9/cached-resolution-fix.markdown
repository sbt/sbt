  [@cunei]: https://github.com/cunei
  [@eed3si9n]: https://github.com/eed3si9n
  [@gkossakowski]: https://github.com/gkossakowski
  [@jsuereth]: https://github.com/jsuereth

  [1721]: https://github.com/sbt/sbt/issues/1721
  [2030]: https://github.com/sbt/sbt/pull/2030

### Fixes with compatibility implications

### Improvements

### Bug fixes

- Fixes memory/performance issue with cached resolution. See below. 

### Cached resolution fixes

On a larger dependency graph, the JSON file growing to be 100MB+
with 97% of taken up by *caller* information.
The caller information is not useful once the graph is successfully resolved.
sbt 0.13.9 creates a single caller to represent all callers,
which fixes `OutOfMemoryException` seen on some builds,
and generally it should make JSON IO faster.

[#2030][2030]/[#1721][1721] by [@eed3si9n][@eed3si9n]
