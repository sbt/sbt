===
sbt
===

.. toctree::
   :hidden:

   index

sbt is a build tool for Scala, Java, and `more <https://github.com/d40cht/sbt-cpp>`_.
It requires Java 1.6 or later.

Install
-------

See the :doc:`setup instructions </Getting-Started/Setup>`.

Features
--------

-  Little or no configuration required for simple projects
-  Scala-based :doc:`build definition </Getting-Started/Basic-Def>` that can use the full flexibility of Scala code
-  Accurate incremental recompilation using information extracted from the compiler
-  Continuous compilation and testing with :doc:`triggered execution </Detailed-Topics/Triggered-Execution>`
-  Packages and publishes jars
-  Generates documentation with scaladoc
-  Supports mixed Scala/:doc:`Java </Detailed-Topics/Java-Sources>` projects
-  Supports :doc:`testing </Detailed-Topics/Testing>` with ScalaCheck, specs, and ScalaTest.
   JUnit is supported by a plugin.
-  Starts the Scala REPL with project classes and dependencies on the classpath
-  Modularization supported with :doc:`sub-projects </Getting-Started/Multi-Project>`
-  External project support (list a git repository as a dependency!)
-  :doc:`Parallel task execution </Detailed-Topics/Parallel-Execution>`, including parallel test execution
-  :doc:`Library management support </Getting-Started/Library-Dependencies>`:
   inline declarations, external Ivy or Maven configuration files, or manual management

Getting Started
---------------

To get started, *please read* the :doc:`Getting Started Guide </Getting-Started/Welcome>`.
You will save yourself a *lot* of time if you have the right understanding of the big picture up-front.
All documentation may be found via the :doc:`table of contents <index>`.

`Stack Overflow <http://stackoverflow.com/tags/sbt>`_ is preferred for questions.
The `mailing list`_ can be used for comments, discussions, and questions.

This documentation can be forked `on GitHub <https://github.com/sbt/sbt/>`_.
Feel free to make corrections and add documentation.

Documentation for 0.7.x has been `archived here <http://www.scala-sbt.org/0.7.7/docs/home.html>`_. 
This documentation applies to sbt |version|.
