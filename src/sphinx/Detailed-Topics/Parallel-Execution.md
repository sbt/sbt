[sbt.ConcurrentRestrictions]: https://github.com/harrah/xsbt/blob/v0.12.0/tasks/ConcurrentRestrictions.scala

# Task ordering

Task ordering is specified by declaring a task's inputs.
Correctness of execution requires correct input declarations.
For example, the following two tasks do not have an ordering specified:

```scala
write := IO.write(file("/tmp/sample.txt"), "Some content.")

read := IO.read(file("/tmp/sample.txt"))
```

sbt is free to execute `write` first and then `read`, `read` first and then `write`, or `read` and `write` simultaneously.
Execution of these tasks is non-deterministic because they share a file.
A correct declaration of the tasks would be:

```scala
write := {
  val f = file("/tmp/sample.txt")
  IO.write(f, "Some content.")
  f
}

read <<= write map { f => IO.read(f) }
```

This establishes an ordering: `read` must run after `write`.
We've also guaranteed that `read` will read from the same file that `write` created.

# Practical constraints

Note: The feature described in this section is experimental.
The default configuration of the feature is subject to change in particular.

## Background

Declaring inputs and dependencies of a task ensures the task is properly ordered and that code executes correctly.
In practice, tasks share finite hardware and software resources and can require control over utilization of these resources.
By default, sbt executes tasks in parallel (subject to the ordering constraints already described) in an effort to utilize all available processors.
Also by default, each test class is mapped to its own task to enable executing tests in parallel.

Prior to sbt 0.12, user control over this process was restricted to:

 1. Enabling or disabling all parallel execution (`parallelExecution := false`, for example).
 2. Enabling or disabling mapping tests to their own tasks (`parallelExecution in Test := false`, for example).

(Although never exposed as a setting, the maximum number of tasks running at a given time was internally configurable as well.)

The second configuration mechanism described above only selected between running all of a project's tests in the same task or in separate tasks.
Each project still had a separate task for running its tests and so test tasks in separate projects could still run in parallel if overall execution was parallel.
There was no way to restriction execution such that only a single test out of all projects executed.

## Configuration

sbt 0.12 contains a general infrastructure for restricting task concurrency beyond the usual ordering declarations.
There are two parts to these restrictions.

 1. A task is tagged in order to classify its purpose and resource utilization.  For example, the `compile` task may be tagged as `Tags.Compile` and `Tags.CPU`.
 2. A list of rules restrict the tasks that may execute concurrently.  For example, `Tags.limit(Tags.CPU, 4)` would allow up to four computation-heavy tasks to run at a time.

The system is thus dependent on proper tagging of tasks and then on a good set of rules.

### Tagging Tasks

In general, a tag is associated with a weight that represents the task's relative utilization of the resource represented by the tag.
Currently, this weight is an integer, but it may be a floating point in the future.
`Initialize[Task[T]]` defines two methods for tagging the constructed Task: `tag` and `tagw`.
The first method, `tag`, fixes the weight to be 1 for the tags provided to it as arguments.
The second method, `tagw`, accepts pairs of tags and weights.
For example, the following associates the `CPU` and `Compile` tags with the `compile` task (with a weight of 1).

```scala
compile <<= myCompileTask tag(Tags.CPU, Tags.Compile)
```

Different weights may be specified by passing tag/weight pairs to `tagw`:

```scala
download <<= downloadImpl.tagw(Tags.Network -> 3)
```

### Defining Restrictions

Once tasks are tagged, the `concurrentRestrictions` setting sets restrictions on the tasks that may be concurrently executed based on the weighted tags of those tasks.
For example,

```scala
concurrentRestrictions := Seq(
  Tags.limit(Tags.CPU, 2),
  Tags.limit(Tags.Network, 10),
  Tags.limit(Tags.Test, 1),
  Tags.limitAll( 15 )
)
```

The example limits:
 
 * the number of CPU-using tasks to be no more than 2
 * the number of tasks using the network to be no more than 10
 * test execution to only one test at a time across all projects
 * the total number of tasks to be less than or equal to 15

Note that these restrictions rely on proper tagging of tasks.
Also, the value provided as the limit must be at least 1 to ensure every task is able to be executed.
sbt will generate an error if this condition is not met.

Most tasks won't be tagged because they are very short-lived.
These tasks are automatically assigned the label `Untagged`.
You may want to include these tasks in the CPU rule by using the `limitSum` method.
For example:

```scala
  ...
  Tags.limitSum(2, Tags.CPU, Tags.Untagged)
  ...
```

Note that the limit is the first argument so that tags can be provided as varargs.

Another useful convenience function is `Tags.exclusive`.
This specifies that a task with the given tag should execute in isolation.
It starts executing only when no other tasks are running (even if they have the exclusive tag) and no other tasks may start execution until it completes.
For example, a task could be tagged with a custom tag `Benchmark` and a rule configured to ensure such a task is executed by itself:

```scala
  ...
  Tags.exclusive(Benchmark)
  ...
```

Finally, for the most flexibility, you can specify a custom function of type `Map[Tag,Int] => Boolean`.
The `Map[Tag,Int]` represents the weighted tags of a set of tasks.
If the function returns `true`, it indicates that the set of tasks is allowed to execute concurrently.
If the return value is `false`, the set of tasks will not be allowed to execute concurrently.
For example, `Tags.exclusive(Benchmark)` is equivalent to the following:

```scala
  ...
  Tags.customLimit { (tags: Map[Tag,Int]) =>
    val exclusive = tags.getOrElse(Benchmark, 0)
     //  the total number of tasks in the group
    val all = tags.getOrElse(Tags.All, 0)
     // if there are no exclusive tasks in this group, this rule adds no restrictions
    exclusive == 0 ||
      // If there is only one task, allow it to execute.
      all == 1
  }
  ...
```

There are some basic rules that custom functions must follow, but the main one to be aware of in practice is that if there is only one task, it must be allowed to execute.
sbt will generate a warning if the user defines restrictions that prevent a task from executing at all and will then execute the task anyway.

### Built-in Tags and Rules

Built-in tags are defined in the `Tags` object.
All tags listed below must be qualified by this object.
For example, `CPU` refers to the `Tags.CPU` value.

The built-in semantic tags are:

 * `Compile` - describes a task that compiles sources.
 * `Test` - describes a task that performs a test.
 * `Publish`
 * `Update`
 * `Untagged` - automatically added when a task doesn't explicitly define any tags.
 * `All`- automatically added to every task.

The built-in resource tags are:

 * `Network` - describes a task's network utilization.
 * `Disk` - describes a task's filesystem utilization.
 * `CPU` - describes a task's computational utilization.

The tasks that are currently tagged by default are:

 * `compile`: `Compile`, `CPU`
 * `test`: `Test`
 * `update`: `Update`, `Network`
 * `publish`, `publish-local`: `Publish`, `Network`

Of additional note is that the default `test` task will propagate its tags to each child task created for each test class.

The default rules provide the same behavior as previous versions of sbt:

```scala
concurrentRestrictions <<= parallelExecution { par =>
  val max = Runtime.getRuntime.availableProcessors
  Tags.limitAll(if(par) max else 1) :: Nil
}
```

As before, `parallelExecution in Test` controls whether tests are mapped to separate tasks.
To restrict the number of concurrently executing tests in all projects, use:

```scala
concurrentRestrictions += Tags.limit(Tags.Test, 1)
```

## Custom Tags

To define a new tag, pass a String to the `Tags.Tag` method.  For example:

```scala
val Custom = Tags.Tag("custom")
```

Then, use this tag as any other tag.  For example:

```scala
aCustomTask <<= aCustomTask.tag(Custom)

concurrentRestrictions += 
  Tags.limit(Custom, 1)
```

## Future work

This is an experimental feature and there are several aspects that may change or require further work.

### Tagging Tasks

Currently, a tag applies only to the immediate computation it is defined on.
For example, in the following, the second compile definition has no tags applied to it.
Only the first computation is labeled.

```scala
compile <<= myCompileTask tag(Tags.CPU, Tags.Compile)

compile ~= { ... do some post processing ... }
```

Is this desirable? expected?  If not, what is a better, alternative behavior?

### Fractional weighting

Weights are currently `int`s, but could be changed to be `double`s if fractional weights would be useful.
It is important to preserve a consistent notion of what a weight of 1 means so that built-in and custom tasks share this definition and useful rules can be written.

### Default Behavior

User feedback on what custom rules work for what workloads will help determine a good set of default tags and rules.

### Adjustments to Defaults

Rules should be easier to remove or redefine, perhaps by giving them names.
As it is, rules must be appended or all rules must be completely redefined.

Redefining the tags of a task looks like:

```scala
compile <<= compile.tag(Tags.Network)
```

This will overwrite the previous weight if the tag (Network) was already defined.

For removing tags, an implementation of `removeTag` should follow from the implementation of `tag` in a straightforward manner.

### Other characteristics

The system of a tag with a weight was selected as being reasonably powerful and flexible without being too complicated.
This selection is not fundamental and could be enhance, simplified, or replaced if necessary.
The fundamental interface that describes the constraints the system must work within is `sbt.ConcurrentRestrictions`.
This interface is used to provide an intermediate scheduling queue between task execution (`sbt.Execute`) and the underlying thread-based parallel execution service (`java.util.concurrent.CompletionService`).
This intermediate queue restricts new tasks from being forwarded to the `j.u.c.CompletionService` according to the `sbt.ConcurrentRestrictions` implementation.
See the [sbt.ConcurrentRestrictions] API documentation for details.