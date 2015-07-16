  [@stuhood]: https://github.com/stuhood
  [@jsuereth]: https://github.com/jsuereth
  [@adriaanm]: https://github.com/adriaanm

  [1967]: https://github.com/sbt/sbt/issues/1967
  [2085]: https://github.com/sbt/sbt/pull/2085

### Changes with compatibility implications

### Improvements

### Fixes

- Changing the value of a constant (final-static-primitive) field will now
  correctly trigger incremental compilation for downstream classes. This is to
  account for the fact that java compilers may inline constant fields in
  downstream classes. [#1967][1967]/[#2085][2085] by [@stuhood][@stuhood]
