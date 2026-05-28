### Clearer error when a referenced file does not exist

If a file added to a task's inputs/outputs (e.g. `Compile / resources += file("nope.txt")`)
does not exist, tasks such as `package` previously failed while hashing the task's cache
key with an opaque `sjsonnew.SerializationException` that dumped the entire input list, with
the real cause (`NoSuchFileException`) buried several `Caused by:` levels down. This was
routinely mistaken for a corrupt cache.

sbt now reports the missing file directly:

```
[error] file referenced by the build does not exist: nope.txt
```

This addresses [#9217][i9217].

[i9217]: https://github.com/sbt/sbt/issues/9217
