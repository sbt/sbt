### Improvements

- ScriptedPlugin: Add the ability to paginate scripted tests.
  It is now possible to run a subset of scripted tests in a directory at once,
  for example:
  ```
  scripted source-dependencies/*1of3
  ```
  Will create three pages and run page 1. This is especially useful when running
  scripted tests on a CI, to benefit from the available parallelism.
  [3013]: https://github.com/sbt/sbt/pull/3013
  [@smarter]: https://github.com/smarter
