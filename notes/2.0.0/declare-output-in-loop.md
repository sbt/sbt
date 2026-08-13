### Every `Def.declareOutput` call registers, including inside loops

The cached-task macro allocated one slot per syntactic `Def.declareOutput` (or
`Def.declareOutputDirectory`) call site, so a call inside a loop or `.map` over a
runtime-determined list of files overwrote the same slot on every iteration and
only the last file was cached and restored. Declared outputs now accumulate per
execution, so a dynamic number of outputs declared from one call site all
survive a cache hit. A `declareOutput` in a conditional branch that is not taken
no longer contributes a null entry to the task's outputs either.

This addresses the loop half of [#9462][i9462].

[i9462]: https://github.com/sbt/sbt/issues/9462
