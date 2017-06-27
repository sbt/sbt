### Changes with compatibility implications

- Moves the old, sbt 0.10 tuple enrichement DSL, which was deprecated in sbt 0.13.13, out of implicit scope and
    to `sbt.TupleSyntax`, so that it is now an opt-in. [#2762][]/[#3291][] by [@dwijnand][]

[@dwijnand]: https://github.com/dwijnand

[#2762]: https://github.com/sbt/sbt/issues/2762
[#3291]: https://github.com/sbt/sbt/pull/3291
