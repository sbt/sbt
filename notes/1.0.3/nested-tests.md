### Bug fixes

In 0.13.x, zinc would discover only top-level objects and classes
containing tests, and pass them to the test framework. In 1.x,
however, zinc can discover also nested objects and classes; that
causes the "name" of a ClassLike to no longer be usable for reflection.

Version 1.0.3 filters out nested objects/classes from the list,
restoring compatibility with 0.13. A zinc extension of ClassLike
will probably be introduced in 1.1 or 1.2, in order to provide
the test framework with enough information to deal with nested
classes.

[@cunei]: https://github.com/cunei
[3583]: https://github.com/sbt/sbt/issues/3583

