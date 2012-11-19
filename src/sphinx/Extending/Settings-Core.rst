=============
Settings Core
=============

This page describes the core settings engine a bit. This may be useful
for using it outside of sbt. It may also be useful for understanding how
sbt works internally.

The documentation is comprised of two parts. The first part shows an
example settings system built on top of the settings engine. The second
part comments on how sbt's settings system is built on top of the
settings engine. This may help illuminate what exactly the core settings
engine provides and what is needed to build something like the sbt
settings system.

Example
-------

Setting up
~~~~~~~~~~

To run this example, first create a new project with the following
build.sbt file:

::

    libraryDependencies += "org.scala-sbt" %% "collections" % sbtVersion.value

    resolvers += sbtResolver.value

Then, put the following examples in source files
``SettingsExample.scala`` and ``SettingsUsage.scala``. Finally, run sbt
and enter the REPL using ``console``. To see the output described below,
enter ``SettingsUsage``.

Example Settings System
~~~~~~~~~~~~~~~~~~~~~~~

The first part of the example defines the custom settings system. There
are three main parts:

1. Define the Scope type.
2. Define a function that converts that Scope (plus an AttributeKey) to
   a String.
3. Define a delegation function that defines the sequence of Scopes in
   which to look up a value.

There is also a fourth, but its usage is likely to be specific to sbt at
this time. The example uses a trivial implementation for this part.

``SettingsExample.scala``

::

      import sbt._

    /** Define our settings system */

    // A basic scope indexed by an integer.
    final case class Scope(index: Int)

    // Extend the Init trait.
    //  (It is done this way because the Scope type parameter is used everywhere in Init.
    //  Lots of type constructors would become binary, which as you may know requires lots of type lambdas
    //  when you want a type function with only one parameter.
    //  That would be a general pain.)
    object SettingsExample extends Init[Scope]
    {
        // Provides a way of showing a Scope+AttributeKey[_]
        val showFullKey: Show[ScopedKey[_]] = new Show[ScopedKey[_]] {
            def apply(key: ScopedKey[_]) = key.scope.index + "/" + key.key.label
        }

        // A sample delegation function that delegates to a Scope with a lower index.
        val delegates: Scope => Seq[Scope] = { case s @ Scope(index) =>
            s +: (if(index <= 0) Nil else delegates(Scope(index-1)) )
        }

        // Not using this feature in this example.
        val scopeLocal: ScopeLocal = _ => Nil

        // These three functions + a scope (here, Scope) are sufficient for defining our settings system.
    }

Example Usage
~~~~~~~~~~~~~

This part shows how to use the system we just defined. The end result is
a ``Settings[Scope]`` value. This type is basically a mapping
``Scope -> AttributeKey[T] -> Option[T]``. See the `Settings API
documentation <../../api/sbt/Settings.html>`_
for details.

``SettingsUsage.scala``

::

    /** Usage Example **/

       import sbt._
       import SettingsExample._
       import Types._

    object SettingsUsage
    {

          // Define some keys
       val a = AttributeKey[Int]("a")
       val b = AttributeKey[Int]("b")

          // Scope these keys
       val a3 = ScopedKey(Scope(3), a)
       val a4 = ScopedKey(Scope(4), a)
       val a5 = ScopedKey(Scope(5), a)

       val b4 = ScopedKey(Scope(4), b)

          // Define some settings
       val mySettings: Seq[Setting[_]] = Seq(
          setting( a3, value( 3 ) ),
          setting( b4, app(a4 :^: KNil) { case av :+: HNil => av * 3 } ),
          update(a5)(_ + 1)
       )

          // "compiles" and applies the settings.
          //  This can be split into multiple steps to access intermediate results if desired.
          //  The 'inspect' command operates on the output of 'compile', for example.
       val applied: Settings[Scope] = make(mySettings)(delegates, scopeLocal, showFullKey)

       // Show results.
       for(i <- 0 to 5; k <- Seq(a, b)) {
          println( k.label + i + " = " + applied.get( Scope(i), k) )
       }

This produces the following output when run:
``a0 = None b0 = None a1 = None b1 = None a2 = None b2 = None a3 = Some(3) b3 = None a4 = Some(3) b4 = Some(9) a5 = Some(4) b5 = Some(9)``

-  For the None results, we never defined the value and there was no
   value to delegate to.
-  For a3, we explicitly defined it to be 3.
-  a4 wasn't defined, so it delegates to a3 according to our delegates
   function.
-  b4 gets the value for a4 (which delegates to a3, so it is 3) and
   multiplies by 3
-  a5 is defined as the previous value of a5 + 1 and since no previous
   value of a5 was defined, it delegates to a4, resulting in 3+1=4.
-  b5 isn't defined explicitly, so it delegates to b4 and is therefore
   equal to 9 as well

sbt Settings Discussion
-----------------------

Scopes
~~~~~~

.. _Global: ../../api/sbt/Global$.html
.. _This: ../../api/sbt/This$.html
.. _Select: ../../api/sbt/Select.html

sbt defines a more complicated scope than the one shown here for the
standard usage of settings in a build. This scope has four components:
the project axis, the configuration axis, the task axis, and the extra
axis. Each component may be
`Global`_ (no specific value), `This`_ (current context), or `Select`_
(containing a specific value). sbt resolves `This_` to either
`Global`_ or `Select`_ depending on the context.

For example, in a project, a `This`_ project axis becomes a
`Select`_ referring to the defining project. All other axes that are
`This`_ are translated to `Global`_.
Functions like inConfig and inTask transform This into a
`Select`_ for a specific value. For example, ``inConfig(Compile)(someSettings)``
translates the configuration axis for all settings in *someSettings* to
be ``Select(Compile)`` if the axis value is `This`_.

So, from the example and from sbt's scopes, you can see that the core
settings engine does not impose much on the structure of a scope. All it
requires is a delegates function ``Scope => Seq[Scope]`` and a
``display`` function. You can choose a scope type that makes sense for
your situation.

Constructing settings
~~~~~~~~~~~~~~~~~~~~~

The *app*, *value*, *update*, and related methods are the core methods
for constructing settings. This example obviously looks rather different
from sbt's interface because these methods are not typically used
directly, but are wrapped in a higher-level abstraction.

With the core settings engine, you work with HLists to access other
settings. In sbt's higher-level system, there are wrappers around HList
for TupleN and FunctionN for N = 1-9 (except Tuple1 isn't actually
used). When working with arbitrary arity, it is useful to make these
wrappers at the highest level possible. This is because once wrappers
are defined, code must be duplicated for every N. By making the wrappers
at the top-level, this requires only one level of duplication.

Additionally, sbt uniformly integrates its task engine into the settings
system. The underlying settings engine has no notion of tasks. This is
why sbt uses a ``SettingKey`` type and a ``TaskKey`` type. Methods on an
underlying ``TaskKey[T]`` are basically translated to operating on an
underlying ``SettingKey[Task[T]]`` (and they both wrap an underlying
``AttributeKey``).

For example, ``a := 3`` for a SettingKey *a* will very roughly translate
to ``setting(a, value(3))``. For a TaskKey *a*, it will roughly
translate to ``setting(a, value( task { 3 } ) )``. See
`main/Structure.scala <../../sxr/Structure.scala>`_
for details.

Settings definitions
~~~~~~~~~~~~~~~~~~~~

sbt also provides a way to define these settings in a file (build.sbt
and Build.scala). This is done for build.sbt using basic parsing and
then passing the resulting chunks of code to ``compile/Eval.scala``. For
all definitions, sbt manages the classpaths and recompilation process to
obtain the settings. It also provides a way for users to define project,
task, and configuration delegation, which ends up being used by the
delegates function.
