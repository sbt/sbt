

## Forked run/test working directory

Forked `run` and `test` no longer run in the project's `baseDirectory`; they inherit
sbt's working directory, matching non-forked behavior. To restore the sbt 1.x behavior:

```scala
run / forkWorkingDirectory := Some(baseDirectory.value)
Test / forkWorkingDirectory := Some(baseDirectory.value)
```

## files extension on Classpath

```scala
+ given FileConverter = fileConverter.value
  val cp = (Compile / classpath).value.files
```
