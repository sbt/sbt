
  [@Duhemm]: http://github.com/Duhemm
  [1171]: https://github.com/sbt/sbt/issues/1171
  [2261]: https://github.com/sbt/sbt/pull/2261

### Fixes with compatibility implications

### Improvements
- Register signatures of method before and after erasure if they involve value classes [#2261][2261] by [@Duhemm][@Duhemm]

### Bug fixes
- Incremental compiler misses change to value class, and results to NoSuchMethodError at runtime [#1171][1171]