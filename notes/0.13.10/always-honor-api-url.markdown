[@jkinkead]: https://github.com/jkinkead
[2262]: https://github.com/sbt/sbt/pull/2262

### Fixes with compatibility implications

### Improvements

- The setting `apiURL` used to be ignored if `autoAPIMappings` was `false`. It will now be inserted into the built POM file regardless of the value of `autoAPIMappings`.  [#2262] by [@jkinkead]

### Bug fixes
