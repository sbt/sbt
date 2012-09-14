[java.io.File]: http://download.oracle.com/javase/6/docs/api/java/io/File.html
[java.io.FileFilter]: http://download.oracle.com/javase/6/docs/api/java/io/FileFilter.html
[RichFile]: http://harrah.github.com/xsbt/latest/api/sbt/RichFile.html
[PathFinder]: http://harrah.github.com/xsbt/latest/api/sbt/PathFinder.html
[Path]: http://harrah.github.com/xsbt/latest/api/sbt/Path$.html
[IO]: http://harrah.github.com/xsbt/latest/api/sbt/IO$.html

# Paths

This page describes files, sequences of files, and file filters.  The base type used is [java.io.File], but several methods are augmented through implicits:

 * [RichFile] adds methods to `File`
 * [PathFinder] adds methods to `File` and `Seq[File]`
 * [Path] and [IO] provide general methods related to files and I/O.

## Constructing a File

sbt 0.10+ uses [java.io.File] to represent a file instead of the custom `sbt.Path` class that was in sbt 0.7 and earlier.
sbt defines the alias `File` for `java.io.File` so that an extra import is not necessary.
The `file` method is an alias for the single-argument `File` constructor to simplify constructing a new file from a String:

```scala
val source: File = file("/home/user/code/A.scala")
```

Additionally, sbt augments File with a `/` method, which is an alias for the two-argument `File` constructor for building up a path:

```scala
def readme(base: File): File = base / "README"
```

Relative files should only be used when defining the base directory of a `Project`, where they will be resolved properly.

```scala
val root = Project("root", file("."))
```

Elsewhere, files should be absolute or be built up from an absolute base `File`.  The `baseDirectory` setting defines the base directory of the build or project depending on the scope.

For example, the following setting sets the unmanaged library directory to be the "custom_lib" directory in a project's base directory:

```scala
unmanagedBase <<= baseDirectory( (base: File) => base /"custom_lib" )
```

Or, more concisely:

```scala
unmanagedBase <<= baseDirectory( _ /"custom_lib" )
```

This setting sets the location of the shell history to be in the base directory of the build, irrespective of the project the setting is defined in:

```scala
historyPath <<= (baseDirectory in ThisBuild)(t => Some(t / ".history")),
```

## Path Finders

A `PathFinder` computes a `Seq[File]` on demand.  It is a way to build a sequence of files.  There are several methods that augment `File` and `Seq[File]` to construct a `PathFinder`.  Ultimately, call `get` on the resulting `PathFinder` to evaluate it and get back a `Seq[File]`.

### Selecting descendants

The `**` method accepts a `java.io.FileFilter` and selects all files matching that filter.  

```scala
def scalaSources(base: File): PathFinder = (base / "src") ** "*.scala"
```

### get

This selects all files that end in `.scala` that are in `src` or a descendent directory.  The list of files is not actually evaluated until `get` is called:

```scala
def scalaSources(base: File): Seq[File] = {
  val finder: PathFinder = (base / "src") ** "*.scala" 
  finder.get
}
```

If the filesystem changes, a second call to `get` on the same `PathFinder` object will reflect the changes.  That is, the `get` method reconstructs the list of files each time.  Also, `get` only returns `File`s that existed at the time it was called.

### Selecting children

Selecting files that are immediate children of a subdirectory is done with a single `*`:

```scala
def scalaSources(base: File): PathFinder = (base / "src") * "*.scala"
```

This selects all files that end in `.scala` that are in the `src` directory.

### Existing files only

If a selector, such as `/`, `**`, or `*, is used on a path that does not represent a directory, the path list will be empty:

```scala
def emptyFinder(base: File) = (base / "lib" / "ivy.jar") * "not_possible"
```

### Name Filter

The argument to the child and descendent selectors `*` and `**` is actually a `NameFilter`.  An implicit is used to convert a `String` to a `NameFilter` that interprets `*` to represent zero or more characters of any value.  See the Name Filters section below for more information.

### Combining PathFinders

Another operation is concatenation of `PathFinder`s:

```scala
def multiPath(base: File): PathFinder =
   (base / "src" / "main") +++
   (base / "lib") +++
   (base / "target" / "classes")
```

When evaluated using `get`, this will return `src/main/`, `lib/`, and `target/classes/`.  The concatenated finder supports all standard methods.  For example,

```scala
def jars(base: File): PathFinder =
   (base / "lib" +++ base / "target") * "*.jar"
```

selects all jars directly in the "lib" and "target" directories.

A common problem is excluding version control directories.  This can be accomplished as follows:

```scala
def sources(base: File) =
   ( (base / "src") ** "*.scala") --- ( (base / "src") ** ".svn" ** "*.scala")
```

The first selector selects all Scala sources and the second selects all sources that are a descendent of a `.svn` directory. The `---` method removes all files returned by the second selector from the sequence of files returned by the first selector. 

### Filtering

There is a `filter` method that accepts a predicate of type `File => Boolean` and is non-strict:

```scala
  // selects all directories under "src"
def srcDirs(base: File) = ( (base / "src") ** "*") filter { _.isDirectory }

  // selects archives (.zip or .jar) that are selected by 'somePathFinder' 
def archivesOnly(base: PathFinder) = base filter ClasspathUtilities.isArchive
```

### Empty PathFinder

`PathFinder.empty` is a `PathFinder` that returns the empty sequence when `get` is called:

```scala
assert( PathFinder.empty.get == Seq[File]() )
```

### PathFinder to String conversions

Convert a `PathFinder` to a String using one of the following methods:

 * `toString` is for debugging.  It puts the absolute path of each component on its own line.
 * `absString` gets the absolute paths of each component and separates them by the platform's path separator.
 * `getPaths` produces a `Seq[String]` containing the absolute paths of each component

### Mappings

The packaging and file copying methods in sbt expect values of type `Seq[(File,String)]` and `Seq[(File,File)]`, respectively.
These are mappings from the input file to its (String) path in the jar or its (File) destination.
This approach replaces the relative path approach (using the `##` method) from earlier versions of sbt.

Mappings are discussed in detail on the [[Mapping Files]] page.

## File Filters

The argument to `*` and `**` is of type [java.io.FileFilter].
sbt provides combinators for constructing `FileFilter`s.

First, a String may be implicitly converted to a `FileFilter`.
The resulting filter selects files with a name matching the string, with a `*` in the string interpreted as a wildcard.
For example, the following selects all Scala sources with the word "Test" in them:

```scala
def testSrcs(base: File): PathFinder =  (base / "src") * "*Test*.scala"
```

There are some useful combinators added to `FileFilter`.  The `||` method declares alternative `FileFilter`s.  The following example selects all Java or Scala source files under "src":

```scala
def sources(base: File): PathFinder  =  (base / "src") ** ("*.scala" || "*.java")
```

The `--`method excludes a files matching a second filter from the files matched by the first:

```scala
def imageResources(base: File): PathFinder =
   (base/"src"/"main"/"resources") * ("*.png" -- "logo.png")
```

This will get `right.png` and `left.png`, but not `logo.png`, for example.
