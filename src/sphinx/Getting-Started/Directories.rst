===================
Directory structure
===================

This page assumes you've :doc:`installed sbt <Setup>` and
seen the :doc:`Hello, World <Hello>` example.

Base directory
--------------

In sbt's terminology, the "base directory" is the directory containing
the project. So if you created a project ``hello`` containing
``hello/build.sbt`` and ``hello/hw.scala`` as in the :doc:`Hello, World <Hello>`
example, ``hello`` is your base directory.

Source code
-----------

Source code can be placed in the project's base directory as with
``hello/hw.scala``. However, most people don't do this for real
projects; too much clutter.

sbt uses the same directory structure as
`Maven <http://maven.apache.org/>`_ for source files by default (all
paths are relative to the base directory):

.. code-block:: text

      src/
        main/
          resources/
             <files to include in main jar here>
          scala/
             <main Scala sources>
          java/
             <main Java sources>
        test/
          resources
             <files to include in test jar here>
          scala/
             <test Scala sources>
          java/
             <test Java sources>

Other directories in ``src/`` will be ignored. Additionally, all hidden
directories will be ignored.

sbt build definition files
--------------------------

You've already seen ``build.sbt`` in the project's base directory. Other
sbt files appear in a ``project`` subdirectory.

``project`` can contain ``.scala`` files, which are combined with
``.sbt`` files to form the complete build definition.
See :doc:`.scala build definitions <Full-Def>` for more.

.. code-block:: text

      build.sbt
      project/
        Build.scala

You may see ``.sbt`` files inside ``project/`` but they are not
equivalent to ``.sbt`` files in the project's base directory. Explaining
this will :doc:`come later <Full-Def>`, since you'll need
some background information first.

Build products
--------------

Generated files (compiled classes, packaged jars, managed files, caches,
and documentation) will be written to the ``target`` directory by
default.

Configuring version control
---------------------------

Your ``.gitignore`` (or equivalent for other version control systems)
should contain:

.. code-block:: text

      target/

Note that this deliberately has a trailing ``/`` (to match only
directories) and it deliberately has no leading ``/`` (to match
``project/target/`` in addition to plain ``target/``).

Next
====

Learn about :doc:`running sbt <Running>`.
