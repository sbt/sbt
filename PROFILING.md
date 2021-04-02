Profiling sbt
-------------

There are several ways to profile sbt. The new hotness in profiling is FlameGraph.
You first collect stack trace samples, and then it is processed into svg graph.
See:

- [Using FlameGraphs To Illuminate The JVM by Nitsan Wakart](https://www.youtube.com/watch?v=ugRrFdda_JQ)
- [USENIX ATC '17: Visualizing Performance with Flame Graphs](https://www.youtube.com/watch?v=D53T1Ejig1Q)

### jvm-profiling-tools/async-profiler

The first one I recommend is async-profiler. This is available for macOS and Linux,
and works fairly well.

1. Download the installer from https://github.com/jvm-profiling-tools/async-profiler/releases/tag/v1.2
2. Make symbolic link to `build/` and `profiler.sh` to `$HOME/bin`, assuming you have PATH to `$HOME/bin`:
   `ln -s ~/Applications/async-profiler/profiler.sh $HOME/bin/profiler.sh`
   `ln -s ~/Applications/async-profiler/build $HOME/bin/build`

Next, close all Java applications and anything that may affect the profiling, and run sbt in one terminal:

```
$ sbt exit
```

In another terminal, run:

```
$ jps
92746 sbt-launch.jar
92780 Jps
```

This tells you the process ID of sbt. In this case, it's 92746. While it's running, run

```
$ profiler.sh -d 60 <process id>
Started [cpu] profiling
--- Execution profile ---
Total samples:         31602
Non-Java:              3239 (10.25%)
GC active:             46 (0.15%)
Unknown (native):      14667 (46.41%)
Not walkable (native): 3 (0.01%)
Unknown (Java):        433 (1.37%)
Not walkable (Java):   8 (0.03%)
Thread exit:           1 (0.00%)
Deopt:                 9 (0.03%)

Frame buffer usage:    55.658%

Total: 1932000000 (6.11%)  samples: 1932
  [ 0] java.lang.ClassLoader$NativeLibrary.load
  [ 1] java.lang.ClassLoader.loadLibrary0
  [ 2] java.lang.ClassLoader.loadLibrary
  [ 3] java.lang.Runtime.loadLibrary0
  [ 4] java.lang.System.loadLibrary
....
```

This should show a bunch of stacktraces that are useful.
To visualize this as a flamegraph, run:

```
$ profiler.sh -d 60 -f /tmp/flamegraph.svg <process id>
```

This should produce `/tmp/flamegraph.svg` at the end.

![flamegraph](project/flamegraph_svg.png)

See https://gist.github.com/eed3si9n/82d43acc95a002876d357bd8ad5f40d5

### running sbt with standby

One of the tricky things you come across while profiling is figuring out the process ID,
while wnating to profile the beginning of the application.

For this purpose, we've added `sbt.launcher.standby` JVM flag.
In the next version of sbt, you should be able to run:

```
$ sbt -J-Dsbt.launcher.standby=20s exit
```

This will count down for 20s before doing anything else.

### jvm-profiling-tools/perf-map-agent

If you want to try the mixed flamegraph, you can try perf-map-agent.
This uses `dtrace` on macOS and `perf` on Linux.

You first have to compile https://github.com/jvm-profiling-tools/perf-map-agent.
For macOS, here to how to export `JAVA_HOME` before running `cmake .`:

```
$ export JAVA_HOME=$(/usr/libexec/java_home)
$ cmake .
-- The C compiler identification is AppleClang 9.0.0.9000039
-- The CXX compiler identification is AppleClang 9.0.0.9000039
...
$ make
```

In addition, you have to git clone https://github.com/brendangregg/FlameGraph

In a fresh termimal, run sbt with `-XX:+PreserveFramePointer` flag:

```
$ sbt -J-Dsbt.launcher.standby=20s -J-XX:+PreserveFramePointer exit
```

In the terminal that you will run the perf-map:

```
$ cd quicktest/
$ export JAVA_HOME=$(/usr/libexec/java_home)
$ export FLAMEGRAPH_DIR=$HOME/work/FlameGraph
$ jps
94592 Jps
94549 sbt-launch.jar
$ $HOME/work/perf-map-agent/bin/dtrace-java-flames 94549
dtrace: system integrity protection is on, some features will not be available

dtrace: description 'profile-99 ' matched 2 probes
Flame graph SVG written to DTRACE_FLAME_OUTPUT='/Users/xxx/work/quicktest/flamegraph-94549.svg'.
```

This would produce better flamegraph in theory, but the output looks too messy for `sbt exit` case.
See https://gist.github.com/eed3si9n/b5856ff3d987655513380d1a551aa0df
This might be because it assumes that the operations are already JITed.

### ktoso/sbt-jmh

https://github.com/ktoso/sbt-jmh

Due to JIT warmup etc, benchmarking is difficult. JMH runs the same tests multiple times to
remove these effects and comes closer to measuring the performance of your code.

There's also an integration with jvm-profiling-tools/async-profiler, apparently.

### VisualVM

I'd also mention traditional JVM profiling tool. Since VisualVM is opensource,
I'll mention this one: https://visualvm.github.io/

1. First VisualVM.
2. Start sbt from a terminal.
3. You should see `xsbt.boot.Boot` under Local.
4. Open it, and select either sampler or profiler, and hit CPU button at the point when you want to start.

If you are familiar with YourKit, it also works similarly.
