=======================
Getting Started Summary
=======================

This page wraps up the Getting Started Guide.

To use sbt, there are a small number of concepts you must understand.
These have some learning curve, but on the positive side, there isn't
much to sbt *except* these concepts. sbt uses a small core of powerful
concepts to do everything it does.

If you've read the whole Getting Started series, now you know what you
need to know.

sbt: The Core Concepts
----------------------

-  the basics of Scala. It's undeniably helpful to be familiar with
   Scala syntax. `Programming in
   Scala <http://www.artima.com/shop/programming_in_scala_2ed>`_ written
   by the creator of Scala is a great introduction.
-  :doc:`.sbt build definition <Basic-Def>`
-  your build definition is one big list of ``Setting`` objects, where a
   ``Setting`` transforms the set of key-value pairs sbt uses to perform
   tasks.
-  to create a ``Setting``, call one of a few methods on a key (the
   ``:=`` and ``<<=`` methods are particularly important).
-  there is no mutable state, only transformation; for example, a
   ``Setting`` transforms sbt's collection of key-value pairs into a new
   collection. It doesn't change anything in-place.
-  each setting has a value of a particular type, determined by the key.
-  *tasks* are special settings where the computation to produce the
   key's value will be re-run each time you kick off a task. Non-tasks
   compute the value once, when first loading the build definition.
-  :doc:`Scopes <Scopes>`
-  each key may have multiple values, in distinct scopes.
-  scoping may use three axes: configuration, project, and task.
-  scoping allows you to have different behaviors per-project, per-task,
   or per-configuration.
-  a configuration is a kind of build, such as the main one
   (``Compile``) or the test one (``Test``).
-  the per-project axis also supports "entire build" scope.
-  scopes fall back to or *delegate* to more general scopes.
-  :doc:`.sbt <Basic-Def>` vs. :doc:`.scala <Full-Def>` build definition
-  put most of your settings in ``build.sbt``, but use ``.scala`` build
   definition files to :doc:`define multiple subprojects <Multi-Project>`
   , and to factor out common values, objects, and methods.
-  the build definition is an sbt project in its own right, rooted in
   the ``project`` directory.
-  :doc:`Plugins <Using-Plugins>` are extensions to the
   build definition
-  add plugins with the ``addSbtPlugin`` method in ``project/build.sbt``
   (NOT ``build.sbt`` in the project's base directory).

If any of this leaves you wondering rather than nodding, please ask for
help on the `mailing list`_, go
back and re-read, or try some experiments in sbt's interactive mode.

Good luck!

Advanced Notes
--------------

The rest of this wiki consists of deeper dives and less-commonly-needed
information.

Since sbt is open source, don't forget you can check out the `source code`_
too!
