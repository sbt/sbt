===
sbt
===

.. toctree::
   :hidden:

   index

sbt is a build tool for Scala and Java projects that aims to do the
basics well. It requires Java 1.6 or later.

Install
-------

See the :doc:`setup instructions </Getting-Started/Setup>`.

Features
--------

-  Easy to set up for simple projects
-  :doc:`.sbt build definition </Getting-Started/Basic-Def>` uses a
   Scala-based "domain-specific language" (DSL)
-  More advanced :doc:`.scala build definitions </Getting-Started/Full-Def>`
   and :doc:`extensions </Getting-Started/Custom-Settings>` use the full
   flexibility of unrestricted Scala code
-  Accurate incremental recompilation using information extracted from the compiler
-  Continuous compilation and testing with :doc:`triggered execution </Detailed-Topics/Triggered-Execution>`
-  Packages and publishes jars
-  Generates documentation with scaladoc
-  Supports mixed Scala/:doc:`Java </Detailed-Topics/Java-Sources>` projects
-  Supports :doc:`testing </Detailed-Topics/Testing>` with ScalaCheck, specs, and ScalaTest
   (JUnit is supported by a plugin)
-  Starts the Scala REPL with project classes and dependencies on the classpath
-  :doc:`Sub-project </Getting-Started/Multi-Project>` support (put multiple packages in one project)
-  External project support (list a git repository as a dependency!)
-  :doc:`Parallel task execution </Detailed-Topics/Parallel-Execution>`, including parallel test execution
-  :doc:`Library management support </Getting-Started/Library-Dependencies>`:
   inline declarations, external Ivy or Maven configuration files, or manual management

Getting Started
---------------

To get started, *please read* the :doc:`Getting Started Guide </Getting-Started/Welcome>`.
You will save yourself a *lot* of time if you have the right understanding of the big picture up-front.
All documentation may be found via the :doc:`table of contents <index>`.

The mailing list is at http://groups.google.com/group/simple-build-tool/topics and should be used for discussions and questions.
Questions may also be asked at `Stack Overflow <http://stackoverflow.com/tags/sbt>`_.

This documentation can be forked `on GitHub <https://github.com/harrah/xsbt/>`_.
Feel free to make corrections and add documentation.

If you are familiar with 0.7.x, please see the :doc:`migration page </Detailed-Topics/Migrating-from-sbt-0.7.x-to-0.10.x>`.
Documentation for 0.7.x has been `archived here <http://www.scala-sbt.org/0.7.7/docs/home.html>`_. 
This documentation applies to sbt |version|.

