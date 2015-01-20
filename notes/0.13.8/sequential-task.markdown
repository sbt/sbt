  [@cunei]: https://github.com/cunei
  [@eed3si9n]: https://github.com/eed3si9n
  [@gkossakowski]: https://github.com/gkossakowski
  [@jsuereth]: https://github.com/jsuereth
  [1817]: https://github.com/sbt/sbt/pull/1817
  [1001]: https://github.com/sbt/sbt/issues/1001
  [Custom-Settings0]: http://www.scala-sbt.org/0.13/tutorial/Custom-Settings.html

### Fixes with compatibility implications

### Improvements

### Bug fixes

### Sequential tasks

sbt 0.13.8 adds a new `Def.sequential` function to run tasks under semi-sequential semantics.
Here's an example usage:

    lazy val root = project.
      settings(
        testFile := target.value / "test.txt",
        sideEffect0 := {
          val t = testFile.value
          IO.append(t, "0")
          t
        },
        sideEffect1 := {
          val t = testFile.value
          IO.append(t, "1")
          t
        },
        foo := Def.sequential(compile in Compile, sideEffect0, sideEffect1, test in Test).value
      )

Normally sbt's task engine will reorder tasks based on the dependencies among the tasks,
and run as many tasks in parallel (See [Custom settings and tasks][Custom-Settings0] for more details on this).
`Def.sequential` instead tries to run the tasks in the specified order.
However, the task engine will still deduplicate tasks. For instance, when `foo` is executed, it will only compile once,
even though `test in Test` depends on compile. [#1817][1817]/[#1001][1001] by [@eed3si9n][@eed3si9n]
