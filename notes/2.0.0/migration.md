

## Forked run working directory

Forked `run` no longer runs in the project's `baseDirectory`; it inherits sbt's
working directory, matching non-forked behavior. Forked `test` is unchanged. To
restore the sbt 1.x behavior:

```scala
Compile / run / forkOptions := Def.uncached(
  (Compile / run / forkOptions).value.withWorkingDirectory(Some(baseDirectory.value))
)
```

## files extension on Classpath

```scala
+ given FileConverter = fileConverter.value
  val cp = (Compile / classpath).value.files
```
