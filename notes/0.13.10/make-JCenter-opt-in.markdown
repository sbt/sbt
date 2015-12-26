
  [@eed3si9n]: https://github.com/eed3si9n
  [2217]: https://github.com/sbt/sbt/issues/2217

### Fixes with compatibility implications

- sbt 0.13.10 adds a new setting `useJCenter`, which is set to `false` by default. When set to `true`, JCenter will be placed as the first external resolver to find library dependencies. [#2217][2217] by [@eed3si9n][@eed3si9n]
