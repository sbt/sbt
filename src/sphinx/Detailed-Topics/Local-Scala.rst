===========
Local Scala
===========

To use a locally built Scala version, define the ``scalaHome`` setting,
which is of type ``Option[File]``. This Scala version will only be used
for the build and not for sbt, which will still use the version it was
compiled against.

Example:

::

    scalaHome := Some(file("/path/to/scala"))

Using a local Scala version will override the ``scalaVersion`` setting
and will not work with :doc:`cross building <Cross-Build>`.

sbt reuses the class loader for the local Scala version. If you
recompile your local Scala version and you are using sbt interactively,
run

::

    > reload

to use the new compilation results.
