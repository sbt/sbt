  [@cunei]: https://github.com/cunei
  [@eed3si9n]: https://github.com/eed3si9n
  [@gkossakowski]: https://github.com/gkossakowski
  [@jsuereth]: https://github.com/jsuereth

  [1223]: https://github.com/sbt/sbt/issues/1223
  [1773]: https://github.com/sbt/sbt/pull/1773

### Fixes with compatibility implications

### Improvements

### Bug fixes

- Enables forced GC by default. See below. 

### Force GC

[@cunei][@cunei] in [#1223][1223] discovered that sbt leaks PermGen
when it creates classloaders to call Scala Compilers.
sbt 0.13.9 will call GC on a set interval (default: 60s).
It will also call GC right before cross building.
This behavior can diabled using by setting false to `forcegc`
setting or `sbt.task.forcegc` flag.

[#1773][1773] by [@eed3si9n][@eed3si9n]
