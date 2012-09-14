=============
Mapping Files
=============

Tasks like ``package``, ``packageSrc``, and ``packageDoc`` accept
mappings of type ``Seq[(File, String)]`` from an input file to the path
to use in the resulting artifact (jar). Similarly, tasks that copy files
accept mappings of type ``Seq[(File, File)]`` from an input file to the
destination file. There are some methods on
`PathFinder <../../api/sbt/PathFinder.html>`_
and `Path <../../api/sbt/Path$.html>`_
that can be useful for constructing the ``Seq[(File, String)]`` or
``Seq[(File, File)]`` sequences.

A common way of making this sequence is to start with a ``PathFinder``
or ``Seq[File]`` (which is implicitly convertible to ``PathFinder``) and
then call the ``x`` method. See the
`PathFinder <../../api/sbt/PathFinder.html>`_
API for details, but essentially this method accepts a function
``File => Option[String]`` or ``File => Option[File]`` that is used to
generate mappings.

Relative to a directory
-----------------------

The ``Path.relativeTo`` method is used to map a ``File`` to its path
``String`` relative to a base directory or directories. The
``relativeTo`` method accepts a base directory or sequence of base
directories to relativize an input file against. The first directory
that is an ancestor of the file is used in the case of a sequence of
base directories.

For example:

::

      import Path.relativeTo
    val files: Seq[File] = file("/a/b/C.scala") :: Nil
    val baseDirectories: Seq[File] = file("/a") :: Nil
    val mappings: Seq[(File,String)] = files x relativeTo(baseDirectories)

    val expected = (file("/a/b/C.scala") -> "b/C.scala") :: Nil
    assert( mappings == expected )

Rebase
------

The ``Path.rebase`` method relativizes an input file against one or more
base directories (the first argument) and then prepends a base String or
File (the second argument) to the result. As with ``relativeTo``, the
first base directory that is an ancestor of the input file is used in
the case of multiple base directories.

For example, the following demonstrates building a
``Seq[(File, String)]`` using ``rebase``:

::

    import Path.rebase
    val files: Seq[File] = file("/a/b/C.scala") :: Nil
    val baseDirectories: Seq[File] = file("/a") :: Nil
    val mappings: Seq[(File,String)] = files x rebase(baseDirectories, "pre/")

    val expected = (file("/a/b/C.scala") -> "pre/b/C.scala" ) :: Nil
    assert( mappings == expected )

Or, to build a ``Seq[(File, File)]``:

::

    import Path.rebase
    val files: Seq[File] = file("/a/b/C.scala") :: Nil
    val baseDirectories: Seq[File] = file("/a") :: Nil
    val newBase: File = file("/new/base")
    val mappings: Seq[(File,File)] = files x rebase(baseDirectories, newBase)

    val expected = (file("/a/b/C.scala") -> file("/new/base/b/C.scala") ) :: Nil
    assert( mappings == expected )

Flatten
-------

The ``Path.flat`` method provides a function that maps a file to the
last component of the path (its name). For a File to File mapping, the
input file is mapped to a file with the same name in a given target
directory. For example:

::

    import Path.flat
    val files: Seq[File] = file("/a/b/C.scala") :: Nil
    val mappings: Seq[(File,String)] = files x flat

    val expected = (file("/a/b/C.scala") -> "C.scala" ) :: Nil
    assert( mappings == expected )

To build a ``Seq[(File, File)]`` using ``flat``:

::

    import Path.flat
    val files: Seq[File] = file("/a/b/C.scala") :: Nil
    val newBase: File = file("/new/base")
    val mappings: Seq[(File,File)] = files x flat(newBase)

    val expected = (file("/a/b/C.scala") -> file("/new/base/C.scala") ) :: Nil
    assert( mappings == expected )

Alternatives
------------

To try to apply several alternative mappings for a file, use ``|``,
which is implicitly added to a function of type ``A => Option[B]``. For
example, to try to relativize a file against some base directories but
fall back to flattening:

\`\`\`scala import Path.relativeTo val files: Seq[File] =
file("/a/b/C.scala") :: file("/zzz/D.scala") :: Nil val baseDirectories:
Seq[File] = file("/a") :: Nil val mappings: Seq[(File,String)] = files x
( relativeTo(baseDirectories) \| flat )

val expected = (file("/a/b/C.scala") -> "b/C.scala") ) ::
(file("/zzz/D.scala") -> "D.scala") ) :: Nil assert( mappings ==
expected ) \`\`\`
