==============
Macro Projects
==============

Introduction
============

Some common problems arise when working with macros.

1. The current macro implementation in the compiler requires that macro implementations be compiled before they are used.  The solution is typically to put the macros in a subproject or in their own configuration.
2. Sometimes the macro implementation should be distributed with the main code that uses them and sometimes the implementation should not be distributed at all.

The rest of the page shows example solutions to these problems.

Defining the Project Relationships
==================================

The macro implementation will go in a subproject in the `macro/` directory.
The main project in the project's base directory will depend on this subproject and use the macro.
This configuration is shown in the following build definition:

`project/Build.scala`

::

    import sbt._
    import Keys._

    object MacroBuild extends Build {
       lazy val main = Project("main", file(".")) dependsOn(macroSub)
       lazy val macroSub = Project("macro", file("macro")) settings(
          libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
       )
    }
    

This specifies that the macro implementation goes in `macro/src/main/scala/` and tests go in `macro/src/test/scala/`.
It also shows that we need a dependency on the compiler for the macro implementation.
As an example macro, we'll use `desugar` from `macrocosm <https://github.com/retronym/macrocosm>`_.

`macro/src/main/scala/demo/Demo.scala`

::

    package demo
    
    import language.experimental.macros
    import scala.reflect.macros.Context
    
    object Demo {
    
      // Returns the tree of `a` after the typer, printed as source code.
      def desugar(a: Any): String = macro desugarImpl
    
      def desugarImpl(c: Context)(a: c.Expr[Any]) = {
        import c.universe._
    
        val s = show(a.tree)
        c.Expr(
          Literal(Constant(s))
        )
      }
    }


`macro/src/test/scala/demo/Usage.scala`

::

    package demo
    
    object Usage {
       def main(args: Array[String]) {
          val s = Demo.desugar(List(1, 2, 3).reverse)
          println(s)
       }
    }

This can be then be run at the console:

.. raw:: text

    $ sbt
    > macro/test:run
    immutable.this.List.apply[Int](1, 2, 3).reverse

Actual tests can be defined and run as usual with `macro/test`.

The main project can use the macro in the same way that the tests do.
For example,

`src/main/scala/MainUsage.scala`

::
    
    package demo
    
    object Usage {
       def main(args: Array[String]) {
          val s = Demo.desugar(List(6, 4, 5).sorted)
          println(s)
       }
    }

.. raw:: text
    
    $ sbt
    > run
    immutable.this.List.apply[Int](6, 4, 5).sorted[Int](math.this.Ordering.Int)

Common Interface
================

Sometimes, the macro implementation and the macro usage should share some common code.
In this case, declare another subproject for the common code and have the main project and the macro subproject depend on the new subproject.
For example, the project definitions from above would look like:

::

   lazy val main = Project("main", file(".")) dependsOn(macroSub, commonSub)
   lazy val macroSub = Project("macro", file("macro")) dependsOn(commonSub) settings(
       libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value
   )
   lazy val commonSub = Project("common", file("common"))

Code in `common/src/main/scala/` is available for both the `macro` and `main` projects to use.

Distribution
============

To include the macro code with the main code, add the binary and source mappings from the macro subproject to the main project.
For example, the `main` Project definition above would now look like:

::

   lazy val main = Project("main", file(".")) dependsOn(macroSub) settings(
      // include the macro classes and resources in the main jar
      mappings in (Compile, packageBin) <++= mappings in (macroSub, Compile, packageBin),
      // include the macro sources in the main source jar
      mappings in (Compile, packageSrc) <++= mappings in (macroSub, Compile, packageSrc)
   )


You may wish to disable publishing the macro implementation.
This is done by overriding `publish` and `publishLocal` to do nothing:

::

    lazy val macroSub = Project("macro", file("macro")) settings(
        publish := {},
        publishLocal := {}
    )

The techniques described here may also be used for the common interface described in the previous section.
