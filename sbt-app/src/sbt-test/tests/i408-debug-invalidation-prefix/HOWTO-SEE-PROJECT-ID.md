# How to see projectId in compile debug output (#408)

When you run `./sbt compile` in the sbt repo, the running sbt is the *published* release,
not the code in this repo. So `[projectId]` does not appear in that run.

To see `[projectId]` you must run an sbt that was built from this code.

## Option A: Run the scripted test and read the stream log

The scripted test uses `project/build.properties: sbt.version=2.0.0-RC9-bin-SNAPSHOT`
so it runs with the *built* sbt. With `scriptedKeepTempDirectory := true` the child sbt
writes its task streams to disk. The compile stream log contains all `[projectId]` lines.

```bash
# From sbt repo root, after ./sbt publishLocal:
./sbt "set scriptedKeepTempDirectory := true" "scripted tests/i408-debug-invalidation-prefix"

# sbt prints the temp dir path, e.g. /tmp/sbt_abc123
# Read the compile stream log for each project:
cat /tmp/sbt_abc123/target/out/jvm/scala-2.12.21/a/streams/compile/compileIncremental/_global/streams/out
cat /tmp/sbt_abc123/target/out/jvm/scala-2.12.21/b/streams/compile/compileIncremental/_global/streams/out

# Or grep all at once:
find /tmp/sbt_* -path "*/compileIncremental/*/streams/out" | xargs grep -h "" 2>/dev/null | grep -v "^$"
```

Example output from those files:

```
[info] [a] Incremental compile (#408)
[debug] [a] [zinc] IncrementalCompile -----------
[debug] [a] previous = Stamps for: 0 products, 0 sources, 0 libraries
[debug] [a] current source = Set(.../a/src/main/scala/A.scala)
[debug] [a] > initialChanges = InitialChanges(...)
[debug] [a] all 1 sources are invalidated
[debug] [a] Initial set of included nodes:
[debug] [a] Recompiling all sources: ...
[debug] [a] compilation cycle 1
[info]  [a] compiling 1 Scala source to .../a/classes ...
[debug] [a] Scala compilation took 18.6 s
[info]  [a] done compiling
[info] [b] Incremental compile (#408)
[debug] [b] [zinc] IncrementalCompile -----------
...
```

## Option B: Use built sbt in a separate project

After `./sbt publishLocal`:

1. Create a small multi-project build anywhere with `project/build.properties`:
   ```
   sbt.version=2.0.0-RC9-bin-SNAPSHOT
   ```
2. Run:
   ```bash
   sbt "set ThisBuild / logLevel := Level.Debug" "a/compile" "b/compile"
   ```

You will see `[a]`, `[b]`, `#408` directly in the terminal.
