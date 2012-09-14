============
Opportunites
============

Below is a running list of potential areas of contribution. This list
may become out of date quickly, so you may want to check on the mailing
list if you are interested in a specific topic.

1. There are plenty of possible visualization and analysis
   opportunities.

   -  'compile' produces an Analysis of the source code containing

      -  Source dependencies
      -  Inter-project source dependencies
      -  Binary dependencies (jars + class files)
      -  data structure representing the
         `API <https://github.com/harrah/xsbt/tree/0.13/interface>`_ of
         the source code There is some code already for generating dot
         files that isn't hooked up, but graphing dependencies and
         inheritance relationships is a general area of work.

   -  'update' produces an :doc:`/Detailed-Topics/Update-Report` mapping
      ``Configuration/ModuleID/Artifact`` to the retrieved ``File``
   -  Ivy produces more detailed XML reports on dependencies. These come
      with an XSL stylesheet to view them, but this does not scale to
      large numbers of dependencies. Working on this is pretty
      straightforward: the XML files are created in ``~/.ivy2`` and the
      ``.xsl`` and ``.css`` are there as well, so you don't even need to
      work with sbt. Other approaches described in `the email
      thread <https://groups.google.com/group/simple-build-tool/browse_thread/thread/7761f8b2ce51f02c/129064ea836c9baf>`_
   -  Tasks are a combination of static and dynamic graphs and it would
      be useful to view the graph of a run
   -  Settings are a static graph and there is code to generate the dot
      files, but isn't hooked up anywhere.

2. There is support for dependencies on external projects, like on
   GitHub. To be more useful, this should support being able to update
   the dependencies. It is also easy to extend this to other ways of
   retrieving projects. Support for svn and hg was a recent
   contribution, for example.
3. Dependency management is a general area. Working on Apache Ivy itself
   is another way to help. For example, I'm pretty sure Ivy is
   fundamentally single threaded. Either a) it's not and you can fix sbt
   to take advantage of this or b) make Ivy multi-threaded and faster at
   resolving dependencies.
4. If you like parsers, sbt commands and input tasks are written using
   custom parser combinators that provide tab completion and error
   handling. Among other things, the efficiency could be improved.
5. The javap task hasn't been reintegrated
6. Implement enhanced 0.11-style warn/debug/info/error/trace commands.
   Currently, you set it like any other setting:

::

      set logLevel := Level.Warn
     or
      set logLevel in Test := Level.Warn

You could make commands that wrap this, like:

::

      warn test:run

Also, trace is currently an integer, but should really be an abstract
data type. 7. There is more aggressive incremental compilation in sbt
0.12. I expect it to be more difficult to reproduce bugs. It would be
helpful to have a mode that generates a diff between successive
compilations and records the options passed to scalac. This could be
replayed or inspected to try to find the cause.

Documentation
=============

1. There's a lot to do with this wiki. If you check the wiki out from
   git, there's a directory called Dormant with some content that needs
   going through.

2. the :doc:`main </index>` page mentions external project references (e.g. to a git
   repo) but doesn't have anything to link to that explains how to use
   those.

3. the :doc:`/Dormant/Configurations` page is missing a list of the built-in
   configurations and the purpose of each.

4. grep the wiki's git checkout for "Wiki Maintenance Note" and work on
   some of those

5. API docs are much needed.

6. Find useful answers or types/methods/values in the other docs, and
   pull references to them up into :doc:`/faq` or :doc:`/Name-Index` so people can
   find the docs. In general the :doc:`/faq` should feel a bit more like a
   bunch of pointers into the regular docs, rather than an alternative
   to the docs.

7. A lot of the pages could probably have better names, and/or little
   2-4 word blurbs to the right of them in the sidebar.


