The assumption here is that you are familiar with sbt 0.7 but new to 0.12.

sbt 0.12's many new capabilities can be a bit overwhelming, but this page should help you migrate to 0.12 with a minimum of fuss.

## Why move to 0.12?

 1. Faster builds (because it is smarter at re-compiling only what it must)
 1. Easier configuration.  For simple projects a single `build.sbt` file in your root directory is easier to create than `project/build/MyProject.scala` was.
 1. No more `lib_managed` directory, reducing disk usage and avoiding backup and version control hassles.
 1. `update` is now much faster and it's invoked automatically by sbt.
 1. Terser output. (Yet you can ask for more details if something goes wrong.)

# Step 1: Read the Getting Started Guide for sbt 0.12

Reading the [[Getting Started Guide|Getting Started Welcome]] will
probably save you a lot of confusion.

# Step 2: Install sbt 0.12.0

Download sbt 0.12 as described on [[the setup page|Getting Started Setup]].

You can run 0.12 the same way that you run 0.7.x, either simply:

    java -jar sbt-launch.jar

Or (as most users do) with a shell script, as described on
[[the setup page|Getting Started Setup]].

If you like, rename `sbt-launch.jar` and the script itself to
support multiple versions. For example you could have scripts for
`sbt7` and `sbt12`.

For more details see [[the setup page|Getting Started Setup]].

# Step 3: A technique for switching an existing project

Here is a technique for switching an existing project to 0.12 while retaining the ability to switch back again at will.  Some builds, such as those with subprojects, are not suited for this technique, but if you learn how to transition a simple project it will help you do a more complex one next.

## Preserve `project/` for 0.7.x project

Rename your `project/` directory to something like `project-old`.  This will hide it from sbt 0.12 but keep it in case you want to switch back to 0.7.x.

## Create `build.sbt` for 0.12

Create a `build.sbt` file in the root directory of your
project. See [[.sbt build definition|Getting Started Basic Def]]
in the Getting Started Guide, and for simple examples [[Quick-Configuration-Examples]]. If you have a simple project then converting your existing project file to this format is largely a matter of re-writing your dependencies and maven archive declarations in a modified yet familiar syntax.

This `build.sbt` file combines aspects of the old `project/build/ProjectName.scala` and `build.properties` files.  It looks like a property file, yet contains Scala code in a special format.

A `build.properties` file like:

    #Project properties
    #Fri Jan 07 15:34:00 GMT 2011
    project.organization=org.myproject
    project.name=My Project
    sbt.version=0.7.7
    project.version=1.0
    def.scala.version=2.7.7
    build.scala.versions=2.8.1
    project.initialize=false

Now becomes part of your `build.sbt` file with lines like:

```scala
name := "My Project"

version := "1.0"

organization := "org.myproject"

scalaVersion := "2.9.2"
```

Currently, a `project/build.properties` is still needed to explicitly select the sbt version.  For example:

```text
sbt.version=0.12.0
```

## Run sbt 0.12

Now launch sbt.  If you're lucky it works and you're done.  For help debugging, see below.

## Switching back to sbt 0.7.x

If you get stuck and want to switch back, you can leave your `build.sbt` file alone. sbt 0.7.x will not understand or notice it. Just rename your 0.12.x `project` directory to something like `project10` and rename the backup of your old project from `project-old` to `project` again.

# FAQs

There's a section in the [[FAQ]] about migration from 0.7 that
covers several other important points.
