Scripted tests
==============

sbt has a suite of integration tests, also known as scripted tests.
Scripted integration tests reside in **`sbt-app/src/sbt-test`** and are
written using the same testing infrastructure sbt plugin authors can
use to test their own plugins with sbt.

You can run the integration tests with the `sbt scripted` sbt
command. To run a single test, such as the test in
`sbt-app/src/sbt-test/project/global-plugin`, from the sbt shell run:

```bash
> scripted project/global-plugin
```

The scripted test framework lets you script a build scenario. It was written to test sbt itself on complex scenarios -- such as change detection and partial compilation.

How to create a scripted test
-----------------------------

### step 1: sbt-app/src/sbt-test

Make a directory structure `sbt-app/src/sbt-test/<test-group>/<test-name>`.
For example, `sbt-app/src/sbt-test/project/something`.

Create an initial build in `something`. Like a real build using sbt. I'm sure you already have several of them to test manually. Here's an example `build.sbt`:

```scala
name := "foo"
scalaVersion := "3.8.4"
```

I also have `Hello.scala`:

```scala
@main def hello(): Unit = println("hi")
```

### step 2: Write a script

Now, write a script to describe your scenario in a file called `test` located at the root dir of your test project.

```bash
# check if the file gets created
> packageBin
$ exists target/**/foo/foo_3-0.1.0-SNAPSHOT.jar
```

Here is the syntax for the script:

1. **`#`** starts a one-line comment
2. **`>`** `name` sends a task to sbt (and tests if it succeeds)
3. **`$`** `name arg*` performs a file command (and tests if it succeeds)
4. **`->`** `name` sends a task to sbt, but expects it to fail
5. **`-$`** `name arg*` performs a file command, but expects it to fail

File commands are:

- **`touch`** `path+` creates or updates the timestamp on the files
- **`delete`** `path+` deletes the files
- **`exists`** `path+` checks if the files exist
- **`mkdir`** `path+` creates dirs
- **`absent`** `path+` checks if the files don't exist
- **`newer`** `source target` checks if `source` is newer
- **`must-mirror`** `source target` checks if `source` is identical
- **`pause`** pauses until enter is pressed
- **`sleep`** `time` sleeps (in milliseconds)
- **`exec`** `command args*` runs the command in another process
- **`copy-file`** `fromPath toPath` copies the file
- **`copy`** `fromPath+ toDir` copies the paths to `toDir` preserving relative structure
- **`copy-flat`** `fromPath+ toDir` copies the paths to `toDir` flat

So my script will run `packageBin` task, and checks if a JAR file gets created. We'll cover more complex tests later.

### step 5: run the script

To run the scripts run the following from the sbt shell (in sbt/sbt):

```bash
> scripted project/something
```

**Note**: `scripted` runs all your tests.

This will copy your test build into a temporary dir, and executes the `test` script. If everything works out, you'd see `publishLocal` running, then:

```bash
[info] Tests selected:
[info]  * project/something
[info] Running 1 / 1 (100.00%) scripted tests with LauncherBased(/Users/xxx/work/sbt/launch/target/sbt-launch.jar)
[info] Running project/something
[success] Total time: 12 s
```

Custom assertion
----------------

The file commands are great, but not nearly enough because none of them test the actual contents. An easy way to test the contents is to implement a custom task in your test build.

For my hello project, I'd like to check if the resulting jar prints out "hello". I can use `scala.sys.process.Process` to run the JAR. To express a failure, just throw an error. Here's `build.sbt`:

```scala
import scala.sys.process.Process

@transient
lazy val check = taskKey[Unit]("check")

name := "foo"
scalaVersion := "3.8.4"
check := {
  val pkg = (Compile / packageBin).value
  val conv = fileConverter.value
  val cp0 = (Compile / externalDependencyClasspath).value
    .map(_.data)
    .map(conv.toPath(_))
    .toList
  val cp = (crossTarget.value / "foo_3-0.1.0-SNAPSHOT.jar") :: cp0
  val p = Process("java", Seq("-cp", cp.mkString(":"), "hello"))
  val out = p.!!
  if out.trim == "bye" then ()
  else sys.error("unexpected output: " + out)
}
```

I am intentionally testing if it matches "bye", to see how the test fails.

Here's `test`:

```bash
# check if the file gets created
> packageBin
$ exists target/**/foo/foo_3-0.1.0-SNAPSHOT.jar

# check if it says hi
> check
```

Running `scripted project/something` fails the test as expected:

```scala
[info] [error] java.lang.RuntimeException: unexpected output: hi
[info] [error]
[info] [error]  at scala.sys.package$.error(package.scala:27)
[info] [error]  at $Wrap2988201cb0$.$sbtdef$$anonfun$1(build.sbt:18)
....
[info] [error]  at java.lang.Thread.run(Thread.java:750)
[info] [error] (check) unexpected output: hi
```

Testing the test
----------------

There are a few tips on debugging the scripted tests.

First is to increase the log level. Add the following line to  your `test` script to get the debug log:

```bash
> debug
```

To suspend the test until you hit the enter key, add the following line to your `test` script:

```bash
$ pause
```

Testing changes
---------------

See `source-dependencies/abstract-type`'s script as an example.

```bash
> compile

# remove type arguments from S
$ copy-file changes/A.scala A.scala

# Both A.scala and B.scala should be recompiled, producing a compile error
-> compile
```

The convention is to store files under `changes/`, and use `copy-file` command to emulate the file changes.
