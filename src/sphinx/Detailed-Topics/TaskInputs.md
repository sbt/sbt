# Task Inputs/Dependencies

Tasks with dependencies are now introduced in the
[[getting started guide|Getting Started More About Settings]],
which you may wish to read first. This older page may have some
additional detail.

_Wiki Maintenance Note:_ This page should have its overlap with
the getting started guide cleaned up, and just have any advanced
or additional notes. It should maybe also be consolidated with
[[Tasks]].


An important aspect of the task system introduced in sbt 0.10 is to combine two common, related steps in a build:

1. Ensure some other task is performed.
2. Use some result from that task.

Previous versions of sbt configured these steps separately using

1. Dependency declarations
2. Some form of shared state

To see why it is advantageous to combine them, compare the situation to that of deferring initialization of a variable in Scala.
This Scala code is a bad way to expose a value whose initialization is deferred:

```scala
 // Define a variable that will be initialized at some point
 // We don't want to do it right away, because it might be expensive
var foo: Foo = _

 // Define a function to initialize the variable
def makeFoo(): Unit = ... initialize foo ...
```

Typical usage would be:

```scala
makeFoo()
doSomething( foo )
```

This example is rather exaggerated in its badness, but I claim it is nearly the same situation as our two step task definitions.
Particular reasons this is bad include:

1. A client needs to know to call `makeFoo()` first.
2. `foo` could be changed by other code.  There could be a `def makeFoo2()`, for example.
3. Access to foo is not thread safe.

The first point is like declaring a task dependency, the second is like two tasks modifying the same state (either project variables or files), and the third is a consequence of unsynchronized, shared state.

In Scala, we have the built-in functionality to easily fix this: `lazy val`.

```scala
lazy val foo: Foo = ... initialize foo ...
```

with the example usage:

```scala
doSomething( foo )
```

Here, `lazy val` gives us thread safety, guaranteed initialization before access, and immutability all in one, DRY construct.
The task system in sbt does the same thing for tasks (and more, but we won't go into that here) that `lazy val` did for our bad example.

A task definition must declare its inputs and the type of its output.
sbt will ensure that the input tasks have run and will then provide their results to the function that implements the task, which will generate its own result.
Other tasks can use this result and be assured that the task has run (once) and be thread-safe and typesafe in the process.

The general form of a task definition looks like:

```scala
myTask <<= (aTask, bTask) map { (a: A, b: B) =>
  ... do something with a, b and generate a result ...
}
```

(This is only intended to be a discussion of the ideas behind tasks, so see the [sbt Tasks](https://github.com/harrah/xsbt/wiki/Tasks) page for details on usage.)
Basically, `myTask` is defined by declaring `aTask` and `bTask` as inputs and by defining the function to apply to the results of these tasks.
Here, `aTask` is assumed to produce a result of type `A` and `bTask` is assumed to produce a result of type `B`.

## Application

Apply this in practice:

1. Determine the tasks that produce the values you need
2. `map` the tasks with the function that implements your task.

As an example, consider generating a zip file containing the binary jar, source jar, and documentation jar for your project.
First, determine what tasks produce the jars.
In this case, the input tasks are `packageBin`, `packageSrc`, and `packageDoc` in the main `Compile` scope.
The result of each of these tasks is the File for the jar that they generated.
Our zip file task is defined by mapping these package tasks and including their outputs in a zip file.
As good practice, we then return the File for this zip so that other tasks can map on the zip task.

```scala
zip <<= (packageBin in Compile, packageSrc in Compile, packageDoc in Compile, zipPath) map { 
  (bin: File, src: File, doc: File, out: File) =>
    val inputs: Seq[(File,String)] = Seq(bin, src, doc) x Path.flat
    IO.zip(inputs, out)
    out
}
```

The `val inputs` line defines how the input files are mapped to paths in the zip.
See [Mapping Files](https://github.com/harrah/xsbt/wiki/Mapping-Files) for details.
The explicit types are not required, but are included for clarity.

The `zipPath` input would be a custom task to define the location of the zip file.
For example:

```scala
zipPath <<= target map {
  (t: File) =>
    t / "out.zip"
}
```
