[@jroper]: https://github.com/jroper
[2613]: https://github.com/sbt/sbt/pull/2613

### Fixes with compatibility implications

### Improvements

- Replace cross building support with sbt-doge. This allows builds with projects that have multiple different combinations of cross scala versions to be cross built correctly.  The behaviour of ++ is changed so that it only updates the Scala version of projects that support that Scala version, but the Scala version can be post fixed with ! to force it to change for all projects. A -v argument has been added that prints verbose information about which projects are having their settings changed along with their cross scala versions. [#2613][2613] by [@jroper][@jroper].

### Bug fixes
