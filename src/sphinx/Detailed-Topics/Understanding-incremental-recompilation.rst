=======================================
Understanding Incremental Recompilation
=======================================

Compiling Scala code is slow, and sbt makes it often faster. By
understanding how, you can even understand how to make compilation even
faster. Modifying source files with many dependencies might require
recompiling only those source files—which might take, say, 5
seconds—instead of all the dependencies—which might take, say, 2
minutes. Often you can control which will be your case and make
development much faster by some simple coding practices.

In fact, improving Scala compilation times is one major goal of sbt, and
conversely the speedups it gives are one of the major motivations to use
it. A significant portion of sbt sources and development efforts deals
with strategies for speeding up compilation.

To reduce compile times, sbt uses two strategies:

1. reduce the overhead for restarting Scalac;
2. implement smart and transparent strategies for incremental
   recompilation, so that only modified files and the needed
   dependencies are recompiled.

3. sbt runs Scalac always in the same virtual machine. If one compiles
   source code using sbt, keeps sbt alive, modifies source code and
   triggers a new compilation, this compilation will be faster because
   (part of) Scalac will have already been JIT-compiled. In the future,
   sbt will reintroduce support for reusing the same compiler instance,
   similarly to fsc.

4. When a source file `A.scala` is modified, sbt goes to great effort
   to recompile other source files depending on `A.scala` only if
   required - that is, only if the interface of `A.scala` was
   modified. With other build management tools (especially for Java,
   like ant), when a developer changes a source file in a
   non-binary-compatible way, he needs to manually ensure that
   dependencies are also recompiled - often by manually running the
   `clean` command to remove existing compilation output; otherwise
   compilation might succeed even when dependent class files might need
   to be recompiled. What is worse, the change to one source might make
   dependencies incorrect, but this is not discovered automatically: One
   might get a compilation success with incorrect source code. Since
   Scala compile times are so high, running `clean` is particularly
   undesirable.

By organizing your source code appropriately, you can minimize the
amount of code affected by a change. sbt cannot determine precisely
which dependencies have to be recompiled; the goal is to compute a
conservative approximation, so that whenever a file must be recompiled,
it will, even though we might recompile extra files.

sbt heuristics
--------------

sbt tracks source dependencies at the granularity of source files. For
each source file, sbt tracks files which depend on it directly; if the
**interface** of classes, objects or traits in a file changes, all files
dependent on that source must be recompiled. At the moment sbt uses the
following algorithm to calculate source files dependent on a given source
file:

  - dependencies introduced through inheritance are included *transitively*;
    a dependency is introduced through inheritance if a class/trait in one
    file inherits from a trait/class in another file
  - all other direct dependencies are included; other dependencies are also
    called "meber reference" dependencies because they are introduced by
    referring to a member (class, method, type, etc.) defined in some other
    source file

Here's an example illustrating the definition above::

   //A.scala
   class A {
     def foo: Int = 123
   }

   //B.scala
   class B extends A

   //C.scala
   class C extends B

   //D.scala
   class D(a: A)

   //E.scala
   class E(d: D)

There are the following dependencies through inheritance:

.. code-block:: none

   B.scala -> A.scala
   C.scala -> B.scala

There are also the following member reference dependencies:

.. code-block:: none

   D.scala -> A.scala
   E.scala -> D.scala

Now if the interface of `A.scala` is changed the following files
will get invalidated: `B.scala`, `C.scala`, `D.scala`. Both
`B.scala` and `C.scala` were included through transtive closure
of inheritance dependencies. The `E.scala` was not included because
`E.scala` doesn't depend directly on `A.scala`.

The distinction between depdencies by inheritance or member reference
is a new feature in sbt 0.13 and is responsible for improved recompilation
times in many cases where deep inheritance chains are not used extensively.

sbt does not instead track dependencies to source code at the
granularity of individual output `.class` files, as one might hope.
Doing so would be incorrect, because of some problems with sealed
classes (see below for discussion).

Dependencies on binary files are different - they are tracked both on
the `.class` level and on the source file level. Adding a new
implementation of a sealed trait to source file `A` affects all
clients of that sealed trait, and such dependencies are tracked at the
source file level.

Different sources are moreover recompiled together; hence a compile
error in one source implies that no bytecode is generated for any of
those. When a lot of files need to be recompiled and the compile fix is
not clear, it might be best to comment out the offending location (if
possible) to allow other sources to be compiled, and then try to figure
out how to fix the offending location—this way, trying out a possible
solution to the compile error will take less time, say 5 seconds instead
of 2 minutes.

What is included in the interface of a Scala class
--------------------------------------------------

It is surprisingly tricky to understand which changes to a class require
recompiling its clients. The rules valid for Java are much simpler (even
if they include some subtle points as well); trying to apply them to
Scala will prove frustrating. Here is a list of a few surprising points,
just to illustrate the ideas; this list is not intended to be complete.

1. Since Scala supports named arguments in method invocations, the name
   of method arguments are part of its interface.
2. Adding a method to a trait requires recompiling all implementing
   classes. The same is true for most changes to a method signature in a
   trait.
3. Calls to `super.methodName` in traits are resolved to calls to an
   abstract method called `fullyQualifiedTraitName$$super$methodName`;
   such methods only exist if they are used. Hence, adding the first
   call to `super.methodName` for a specific `methodName` changes
   the interface. At present, this is not yet handled—see gh-466.
4. `sealed` hierarchies of case classes allow to check exhaustiveness
   of pattern matching. Hence pattern matches using case classes must
   depend on the complete hierarchy - this is one reason why
   dependencies cannot be easily tracked at the class level (see Scala
   issue `SI-2559 <https://issues.scala-lang.org/browse/SI-2559>`_ for
   an example.)

Debugging an interface representation
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

If you see spurious incremental recompilations or you want understand
what changes to an extracted interface cause incremental recompilation
then sbt 0.13 has the right tools for that.

In order to debug the interface representation and its changes as you
modify and recompile source code you need to do two things:

   1. Enable incremental compiler's `apiDebug` option.
   2. Add `diff-utils library <https://code.google.com/p/java-diff-utils/>`_
      to sbt's classpath. Check documentation of `sbt.extraClasspath`
      system property in the :doc:`Command-Line-Reference`.

.. warning:: Enabling the `apiDebug` option increases significantly
             memory consumption and degrades performance of the
             incremental compiler. The underlying reason is that in
             order to produce meaningful debugging information about
             interface differences incremental compiler has to retain
             the full representation of the interface instead of just
             hash sum as it does by default.

             Keep this option enabled when you are debugging incremental
             compiler problem only.

Below is complete transcript which shows how to enable interface debugging
in your project. First, we download the `diffutils` jar and pass it
to sbt:

.. code-block:: none

   curl -O https://java-diff-utils.googlecode.com/files/diffutils-1.2.1.jar
   sbt -Dsbt.extraClasspath=diffutils-1.2.1.jar
   [info] Loading project definition from /Users/grek/tmp/sbt-013/project
   [info] Set current project to sbt-013 (in build file:/Users/grek/tmp/sbt-013/)
   > set incOptions := incOptions.value.copy(apiDebug = true)
   [info] Defining *:incOptions
   [info] The new value will be used by compile:incCompileSetup, test:incCompileSetup
   [info] Reapplying settings...
   [info] Set current project to sbt-013 (in build file:/Users/grek/tmp/sbt-013/)

Let's suppose you have the following source code in `Test.scala`::

   class A {
      def b: Int = 123
   }

compile it and then change the `Test.scala` file so it looks like::

   class A {
      def b: String = "abc"
   }

and run :key:`compile` task again. Now if you run `last compile` you should see
the following lines in the debugging log

.. code-block:: none

   > last compile
   [...]
   [debug] Detected a change in a public API:
   [debug] --- /Users/grek/tmp/sbt-013/Test.scala
   [debug] +++ /Users/grek/tmp/sbt-013/Test.scala
   [debug] @@ -23,7 +23,7 @@
   [debug]  ^inherited^ final def ##(): scala.this#Int
   [debug]  ^inherited^ final def synchronized[ java.lang.Object.T0 >: scala.this#Nothing <: scala.this#Any](x$1: <java.lang.Object.T0>): <java.lang.Object.T0>
   [debug]  ^inherited^ final def $isInstanceOf[ java.lang.Object.T0 >: scala.this#Nothing <: scala.this#Any](): scala.this#Boolean
   [debug]  ^inherited^ final def $asInstanceOf[ java.lang.Object.T0 >: scala.this#Nothing <: scala.this#Any](): <java.lang.Object.T0>
   [debug]  def <init>(): this#A
   [debug] -def b: scala.this#Int
   [debug] +def b: java.lang.this#String
   [debug]  }

You can see an unified diff of two interface textual represetantions. As you can see,
the incremental compiler detected a change to the return type of `b` method.

How to take advantage of sbt heuristics
---------------------------------------

The heuristics used by sbt imply the following user-visible
consequences, which determine whether a change to a class affects other
classes.

XXX Please note that this part of the documentation is a first draft;
part of the strategy might be unsound, part of it might be not yet
implemented.

1. Adding, removing, modifying `private` methods does not require
   recompilation of client classes. Therefore, suppose you add a method
   to a class with a lot of dependencies, and that this method is only
   used in the declaring class; marking it `private` will prevent
   recompilation of clients. However, this only applies to methods which
   are not accessible to other classes, hence methods marked with
   `private` or `private[this]`; methods which are private to a
   package, marked with `private[name]`, are part of the API.
2. Modifying the interface of a non-private method requires recompiling
   all clients, even if the method is not used.
3. Modifying one class does require recompiling dependencies of other
   classes defined in the same file (unlike said in a previous version
   of this guide). Hence separating different classes in different
   source files might reduce recompilations.
4. Adding a method which did not exist requires recompiling all clients,
   counterintuitively, due to complex scenarios with implicit
   conversions. Hence in some cases you might want to start implementing
   a new method in a separate, new class, complete the implementation,
   and then cut-n-paste the complete implementation back into the
   original source.
5. Changing the implementation of a method should *not* affect its
   clients, unless the return type is inferred, and the new
   implementation leads to a slightly different type being inferred.
   Hence, annotating the return type of a non-private method explicitly,
   if it is more general than the type actually returned, can reduce the
   code to be recompiled when the implementation of such a method
   changes. (Explicitly annotating return types of a public API is a
   good practice in general.)

All the above discussion about methods also applies to fields and
members in general; similarly, references to classes also extend to
objects and traits.

Why changing the implementation of a method might affect clients, and why type annotations help
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

This section explains why relying on type inference for return types of
public methods is not always appropriate. However this is an important
design issue, so we cannot give fixed rules. Moreover, this change is
often invasive, and reducing compilation times is not often a good
enough motivation. That is why we discuss also some of the implications
from the point of view of binary compatibility and software engineering.

Consider the following source file `A.scala`:

.. code-block:: scala

    import java.io._
    object A {
      def openFiles(list: List[File]) = 
        list.map(name => new FileWriter(name))
    }

Let us now consider the public interface of trait `A`. Note that the
return type of method `openFiles` is not specified explicitly, but
computed by type inference to be `List[FileWriter]`. Suppose that
after writing this source code, we introduce client code and then modify
`A.scala` as follows:

.. code-block:: scala

    import java.io._
    object A {
      def openFiles(list: List[File]) =
        Vector(list.map(name => new BufferedWriter(new FileWriter(name))): _*)
    }

Type inference will now compute as result type
`Vector[BufferedWriter]`; in other words, changing the implementation
lead to a change of the public interface, with two undesirable
consequences:

1. Concerning our topic, client code needs to be recompiled, since
   changing the return type of a method, in the JVM, is a
   binary-incompatible interface change.
2. If our component is a released library, using our new version
   requires recompiling all client code, changing the version number,
   and so on. Often not good, if you distribute a library where binary
   compatibility becomes an issue.
3. More in general, client code might now even be invalid. The following
   code will for instance become invalid after the change:

::

    val res: List[FileWriter] = A.openFiles(List(new File("foo.input")))

Also the following code will break:

.. code-block:: scala

    val a: Seq[Writer] = new BufferedWriter(new FileWriter("bar.input"))
    A.openFiles(List(new File("foo.input")))

How can we avoid these problems?

Of course, we cannot solve them in general: if we want to alter the
interface of a module, breakage might result. However, often we can
remove *implementation details* from the interface of a module. In the
example above, for instance, it might well be that the intended return
type is more general - namely `Seq[Writer]`. It might also not be the
case - this is a design choice to be decided on a case-by-case basis. In
this example I will assume however that the designer chooses
`Seq[Writer]`, since it is a reasonable choice both in the above
simplified example and in a real-world extension of the above code.

The client snippets above will now become 

::

    val res: Seq[Writer] =
        A.openFiles(List(new File("foo.input")))

    val a: Seq[Writer] =
        new BufferedWriter(new FileWriter("bar.input")) +:
        A.openFiles(List(new File("foo.input")))

XXX the rest of the section must be reintegrated or dropped: In general,
changing the return type of a method might be source-compatible, for
instance if the new type is more specific, or if it is less specific,
but still more specific than the type required by clients (note however
that making the type more specific might still invalidate clients in
non-trivial scenarios involving for instance type inference or implicit
conversions—for a more specific type, too many implicit conversions
might be available, leading to ambiguity); however, the bytecode for a
method call includes the return type of the invoked method, hence the
client code needs to be recompiled.

Hence, adding explicit return types on classes with many dependencies
might reduce the occasions where client code needs to be recompiled.
Moreover, this is in general a good development practice when interface
between different modules become important—specifying such interface
documents the intended behavior and helps ensuring binary compatibility,
which is especially important when the exposed interface is used by
other software component.

Why adding a member requires recompiling existing clients
~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~~

In Java adding a member does not require recompiling existing valid
source code. The same should seemingly hold also in Scala, but this is
not the case: implicit conversions might enrich class `Foo` with
method `bar` without modifying class `Foo` itself (see discussion in
issue gh-288 - XXX integrate more). However, if another method `bar`
is introduced in class `Foo`, this method should be used in preference
to the one added through implicit conversions. Therefore any class
depending on `Foo` should be recompiled. One can imagine more
fine-grained tracking of dependencies, but this is currently not
implemented.

Further references
------------------

The incremental compilation logic is implemented in
https://github.com/sbt/sbt/blob/0.13/compile/inc/src/main/scala/inc/Incremental.scala.
Some related documentation for sbt 0.7 is available at:
https://code.google.com/p/simple-build-tool/wiki/ChangeDetectionAndTesting.
Some discussion on the incremental recompilation policies is available
in issue gh-322 and gh-288.
