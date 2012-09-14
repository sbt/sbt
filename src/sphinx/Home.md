sbt is a build tool for Scala and Java projects that aims to do the basics well. It requires Java 1.6 or later.

## Install

See the [[install instructions|Getting Started Setup]].

## Features
* Easy to set up for simple projects
* [[.sbt build definition|Getting Started Basic Def]] uses a Scala-based "domain-specific language" (DSL)
* More advanced [[.scala build definitions|Getting Started Full Def]] and [[extensions|Getting Started Custom Settings]] use the full flexibility of unrestricted Scala code
* Accurate incremental recompilation using information extracted from the compiler
* Continuous compilation and testing with [[triggered execution|Triggered Execution]]
* Packages and publishes jars
* Generates documentation with scaladoc
* Supports mixed Scala/[[Java|Java Sources]] projects
* Supports [[Testing|testing]] with ScalaCheck, specs, and ScalaTest (JUnit is supported by a plugin)
* Starts the Scala REPL with project classes and dependencies on the classpath
* [[Sub-project|Getting Started Multi-Project]] support (put multiple packages in one project)
* External project support (list a git repository as a dependency!)
* Parallel task execution, including parallel test execution
* [[Library management support|Getting Started Library Dependencies]]: inline declarations, external Ivy or Maven configuration files, or manual management

## Getting Started

To get started, read the
 [[Getting Started Guide|Getting Started Welcome]].

_Please read the
[[Getting Started Guide|Getting Started Welcome]]._ You will save
yourself a _lot_ of time if you have the right understanding of
the big picture up-front.

If you are familiar with 0.7.x, please see the
[[migration page|Migrating from sbt 0.7.x to 0.10.x]]. Documentation for
0.7.x is still available on the
[Google Code Site](http://code.google.com/p/simple-build-tool/wiki/DocumentationHome).
This wiki applies to sbt 0.10 and later.

The mailing list is at <http://groups.google.com/group/simple-build-tool/topics>. Please use it for questions and comments!

This wiki is editable if you have a GitHub account.  Feel free to make corrections and add documentation.  Use the mailing list if you have questions or comments.
