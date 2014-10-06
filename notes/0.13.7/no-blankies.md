  [1606]: https://github.com/sbt/sbt/issues/1606
  [1645]: https://github.com/sbt/sbt/pull/1645

### whitespace handling improvements

Starting sbt 0.13.7, build.sbt will be parsed using a customized Scala parser. This eliminates the requirement to use blank line as the delimiter between each settings, and also allows blank lines to be inserted at arbitrary position within a block.

This feature was contributed by [Andrzej Jozwik @ajozwik](https://github.com/ajozwik) inspired by Typesafe's @gkossakowski organizing multiple meetups with Warszaw Scala on [how to patch sbt specifically with the focus on blank line issue](http://blog.japila.pl/2014/07/gkossakowski-on-warszawscala-about-how-to-patch-scalasbt/). [#1606][1606]/[#1645][1645]
