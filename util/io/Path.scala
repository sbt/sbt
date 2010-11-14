/* sbt -- Simple Build Tool
 * Copyright 2008, 2009, 2010  Mark Harrah
 */
package sbt

import Path._
import IO.{pathSplit, wrapNull}
import java.io.File
import java.net.URL
import scala.collection.{generic, immutable, mutable, TraversableLike}

/** A Path represents a file in a project.
* @see sbt.PathFinder*/
sealed abstract class Path extends PathFinder
{
	/** Creates a base directory for this path.  This is used by copy and zip functions
	* to determine the relative path that should be used in the destination.  For example,
	* if the following path is specified to be copied to directory 'd',
	* 
	* <code>((a / b) ###) / x / y</code>
	*
	* the copied path would be 
	*
	* <code>d / x / y</code>
	*
	* The <code>relativePath</code> method is used to return the relative path to the base directory. */
	override def ### : Path = new BaseDirectory(this)
	private[sbt] def addTo(pathSet: mutable.Set[Path])
	{
		if(asFile.exists)
			pathSet += this
	}
	override def / (component: String): Path = if(component == ".") this else new RelativePath(this, component)
	/** True if and only if the file represented by this path exists.*/
	def exists = asFile.exists
	/** True if and only if the file represented by this path is a directory.*/
	def isDirectory = asFile.isDirectory
	/** The last modified time of the file represented by this path.*/
	def lastModified = asFile.lastModified
	/* True if and only if file that this path represents exists and the file represented by the path 'p'
	* does not exist or was modified before the file for this path.*/
	def newerThan(p: Path): Boolean = exists && (!p.exists || lastModified > p.lastModified)
	/* True if and only if file that this path represents does not exist or the file represented by the path 'p'
	* exists and was modified after the file for this path.*/
	def olderThan(p: Path): Boolean = p newerThan this
	/** The file represented by this path.*/
	def asFile: File
	/** The file represented by this path converted to a <code>URL</code>.*/
	def asURL = asFile.toURI.toURL
	/** The string representation of this path relative to the base directory.  The project directory is the
	* default base directory if one is not specified explicitly using the <code>###</code> operator.*/
	lazy val relativePath: String = relativePathString(sep.toString)
	def relativePathString(separator: String): String
	final def projectRelativePath: String = projectRelativePathString(sep.toString)
	def projectRelativePathString(separator: String): String
	def absolutePath: String = asFile.getAbsolutePath
	private[sbt] def prependTo(s: String): String
	
	/** The last component of this path.*/
	def name = asFile.getName
	/** The extension part of the name of this path.  This is the part of the name after the last period, or the empty string if there is no period.*/
	def ext = baseAndExt._2
	/** The base of the name of this path.  This is the part of the name before the last period, or the full name if there is no period.*/
	def base = baseAndExt._1
	def baseAndExt: (String, String) =
	{
		val nme = name
		val dot = nme.lastIndexOf('.')
		if(dot < 0) (nme, "") else (nme.substring(0, dot), nme.substring(dot+1))
	}
	
	/** Equality of Paths is defined in terms of the underlying <code>File</code>.*/
	override final def equals(other: Any) =
		other match
		{
			case op: Path => asFile == op.asFile
			case _ => false
		}
	/** The hash code of a Path is that of the underlying <code>File</code>.*/
	override final def hashCode = asFile.hashCode
}
private final class BaseDirectory(private[sbt] val path: Path) extends Path
{
	override def ### : Path = this
	override def toString = path.toString
	def asFile = path.asFile
	def relativePathString(separator: String) = ""
	def projectRelativePathString(separator: String) = path.projectRelativePathString(separator)
	private[sbt] def prependTo(s: String) = "." + sep + s
}
private[sbt] final class FilePath(file: File) extends Path
{
	lazy val asFile = absolute(file)
	override def toString = absolutePath
	def relativePathString(separator: String) = asFile.getName
	def projectRelativePathString(separator: String) = relativePathString(separator)
	private[sbt] def prependTo(s: String) = absolutePath + sep + s
}
// toRoot is the path between this and the root project path and is used for toString
private[sbt] final class ProjectDirectory(file: File, toRoot: Option[Path]) extends Path
{
	def this(file: File) = this(file, None)
	lazy val asFile = absolute(file)
	override def toString = foldToRoot(_.toString, ".")
	def relativePathString(separator: String) = ""
	def projectRelativePathString(separator: String) = ""
	private[sbt] def prependTo(s: String) = foldToRoot(_.prependTo(s), "." + sep + s)
	private[sbt] def foldToRoot[T](f: Path => T, orElse: T) = toRoot.map(f).getOrElse(orElse)
}
private[sbt] final class RelativePath(val parentPath: Path, val component: String) extends Path
{
	checkComponent(component)
	override def toString = parentPath prependTo component
	lazy val asFile = new File(parentPath.asFile, component)
	private[sbt] def prependTo(s: String) =  parentPath prependTo (component + sep + s)
	def relativePathString(separator: String) = relative(parentPath.relativePathString(separator), separator)
	def projectRelativePathString(separator: String) = relative(parentPath.projectRelativePathString(separator), separator)
	private def relative(parentRelative: String, separator: String) =
	{
		if(parentRelative.isEmpty)
			component
		else
			parentRelative + separator + component
	}
}
	import java.io.File
	import File.pathSeparator
trait PathExtra extends Alternatives with Mapper
{
	implicit def fileToPath(file: File): Path = Path.fromFile(file)
	implicit def pathToFile(path: Path): File = path.asFile
	implicit def pathsToFiles[CC[X] <: TraversableLike[X,CC[X]]](cc: CC[Path])(implicit cb: generic.CanBuildFrom[CC[Path], File, CC[File]]): CC[File] =
		cc.map(_.asFile)
	implicit def filesToPaths[CC[X] <: TraversableLike[X,CC[X]]](cc: CC[File])(implicit cb: generic.CanBuildFrom[CC[File], Path, CC[Path]]): CC[Path] =
		cc.map(fileToPath)
	implicit def filesToFinder(cc: Traversable[File]): PathFinder = finder(cc)
	implicit def pathsToFinder(cc: Traversable[Path]): PathFinder = lazyPathFinder(cc)
}
object Path extends PathExtra
{
	def fileProperty(name: String) = Path.fromFile(System.getProperty(name))
	def userHome = fileProperty("user.home")
	
	def absolute(file: File) = new File(file.toURI.normalize).getAbsoluteFile
	/** Constructs a String representation of <code>Path</code>s.  The absolute path String of each <code>Path</code> is
	* separated by the platform's path separator.*/
	def makeString(paths: Iterable[Path]): String = makeString(paths, pathSeparator)
	/** Constructs a String representation of <code>Path</code>s.  The absolute path String of each <code>Path</code> is
	* separated by the given separator String.*/
	def makeString(paths: Iterable[Path], sep: String): String = paths.map(_.absolutePath).mkString(sep)

	def makeString(paths: Seq[File]): String = makeString(paths, pathSeparator)
	def makeString(paths: Seq[File], sep: String): String = paths.map(_.getAbsolutePath).mkString(sep)
	
	/** Constructs a String representation of <code>Path</code>s.  The relative path String of each <code>Path</code> is
	* separated by the platform's path separator.*/
	def makeRelativeString(paths: Iterable[Path]): String = paths.map(_.relativePathString(sep.toString)).mkString(pathSeparator)
	
	def splitString(projectPath: Path, value: String): Iterable[Path] =
	{
		for(pathString <- pathSplit(value) if pathString.length > 0) yield
			Path.fromString(projectPath, pathString)
	}
	
	/** A <code>PathFinder</code> that always produces the empty set of <code>Path</code>s.*/
	def emptyPathFinder =
		new PathFinder
		{
			private[sbt] def addTo(pathSet: mutable.Set[Path]) {}
		}
	/** A <code>PathFinder</code> that selects the paths provided by the <code>paths</code> argument, which is
	* reevaluated on each call to the <code>PathFinder</code>'s <code>get</code> method.  */
	def lazyPathFinder(paths: => Traversable[Path]): PathFinder =
		new PathFinder
		{
			private[sbt] def addTo(pathSet: mutable.Set[Path]) = pathSet ++= paths
		}
	def finder(files: => Traversable[File]): PathFinder =  lazyPathFinder { fromFiles(files) }
		
	/** The separator character of the platform.*/
	val sep = java.io.File.separatorChar
	
	/** Checks the string to verify that it is a legal path component.  The string must be non-empty,
	* not a slash, and not '.' or '..'.*/
	def checkComponent(c: String): String =
	{
		require(c.length > 0, "Path component must not be empty")
		require(c.indexOf('/') == -1, "Path component '" + c + "' must not have forward slashes in it")
		require(c.indexOf('\\') == -1, "Path component '" + c + "' must not have backslashes in it")
		require(c != "..", "Path component cannot be '..'")
		require(c != ".", "Path component cannot be '.'")
		c
	}
	/** Converts a path string relative to the given base path to a <code>Path</code>. */
	def fromString(basePath: Path, value: String): Path =
	{
		if(value.isEmpty)
			basePath
		else
		{
			val f = new File(value)
			if(f.isAbsolute)
				fromFile(f)
			else
			{
				val components = value.split("""[/\\]""")
				(basePath /: components)( (path, component) => path / component )
			}
		}
	}
	def baseAncestor(path: Path): Option[Path] =
		path match
		{
			case pd: ProjectDirectory => None
			case fp: FilePath => None
			case rp: RelativePath => baseAncestor(rp.parentPath)
			case b: BaseDirectory => Some(b.path)
		}
	
	def relativize(basePath: Path, path: Path): Option[Path] = relativize(basePath, path.asFile)
	def relativize(basePath: Path, file: File): Option[Path] =
		basePathString(basePath) flatMap { baseString => relativize(basePath, baseString, file) }
	def relativize(basePath: Path, basePathString: String, file: File): Option[Path] =
	{
		val pathString = file.getAbsolutePath
		if(pathString.startsWith(basePathString))
			Some(fromString(basePath, pathString.substring(basePathString.length)))
		else
			None
	}
	def relativizeFile(baseFile: File, file: File): Option[File] = relativize(baseFile, file).map { path => new File(path) }
	private[sbt] def relativize(baseFile: File, file: File): Option[String] =
	{
		val pathString = file.getAbsolutePath
		baseFileString(baseFile) flatMap
		{
			baseString =>
			{
				if(pathString.startsWith(baseString))
					Some(pathString.substring(baseString.length))
				else
					None
			}
		}
	}
	private[sbt] def basePathString(basePath: Path): Option[String] = baseFileString(basePath.asFile)
	private def baseFileString(baseFile: File): Option[String] =
	{
		if(baseFile.isDirectory)
		{
			val cp = baseFile.getAbsolutePath
			assert(cp.length > 0)
			if(cp.charAt(cp.length - 1) == File.separatorChar)
				Some(cp)
			else
				Some(cp + File.separatorChar)
		}
		else
			None
	}
	def fromFile(file: String): Path = fromFile(new File(file))
	def fromFile(file: File): Path = new FilePath(file)
	import collection.generic.{CanBuildFrom, FilterMonadic}
	def fromFiles[Repr, That](files: FilterMonadic[File, Repr])(implicit bf: CanBuildFrom[Repr, Path, That]): That =  files.map(fromFile)

	def getFiles(files: Traversable[Path]): immutable.Set[File] = files.map(_.asFile).toSet
	def getURLs(files: Traversable[Path]): Array[URL] = files.map(_.asURL).toArray

	def toURLs(files: Seq[File]): Array[URL] = files.map(_.toURI.toURL).toArray
}

/** A path finder constructs a set of paths.  The set is evaluated by a call to the <code>get</code>
* method.  The set will be different for different calls to <code>get</code> if the underlying filesystem
* has changed.*/
sealed abstract class PathFinder extends NotNull
{
	/** The union of the paths found by this <code>PathFinder</code> with the paths found by 'paths'.*/
	def +++(paths: PathFinder): PathFinder = new Paths(this, paths)
	/** Excludes all paths from <code>excludePaths</code> from the paths selected by this <code>PathFinder</code>.*/
	def ---(excludePaths: PathFinder): PathFinder = new ExcludePaths(this, excludePaths)
	/** Constructs a new finder that selects all paths with a name that matches <code>filter</code> and are
	* descendents of paths selected by this finder.*/
	def **(filter: FileFilter): PathFinder = new DescendentOrSelfPathFinder(this, filter)
	def *** : PathFinder = **(AllPassFilter)
	/** Constructs a new finder that selects all paths with a name that matches <code>filter</code> and are
	* immediate children of paths selected by this finder.*/
	def *(filter: FileFilter): PathFinder = new ChildPathFinder(this, filter)
	/** Constructs a new finder that selects all paths with name <code>literal</code> that are immediate children
	* of paths selected by this finder.*/
	def / (literal: String): PathFinder = new ChildPathFinder(this, new ExactFilter(literal))
	/** Constructs a new finder that selects all paths with name <code>literal</code> that are immediate children
	* of paths selected by this finder.*/
	final def \ (literal: String): PathFinder = this / literal

	/** Makes the paths selected by this finder into base directories.
	* @see Path.###
	*/
	def ### : PathFinder = new BasePathFinder(this)

	def x_![T](mapper: File => Option[T]): Traversable[(File,T)] = x(mapper, false)
	/** Applies `mapper` to each path selected by this PathFinder and returns the path paired with the non-empty result.
	* If the result is empty (None) and `errorIfNone` is true, an exception is thrown.
	* If `errorIfNone` is false, the path is dropped from the returned Traversable.*/
	def x[T](mapper: File => Option[T], errorIfNone: Boolean = true): Traversable[(File,T)] =
	{
		val apply = if(errorIfNone) mapper | fail else mapper
		for(file <- getFiles; mapped <- apply(file)) yield (file, mapped)
	}
	/** Pairs each path selected by this PathFinder with its relativePath.*/
	def xx: Traversable[(File, String)] = get.map(path => (path.asFile, path.relativePath))

	/** Selects all descendent paths with a name that matches <code>include</code> and do not have an intermediate
	* path with a name that matches <code>intermediateExclude</code>.  Typical usage is:
	*
	* <code>descendentsExcept("*.jar", ".svn")</code>*/
	def descendentsExcept(include: FileFilter, intermediateExclude: FileFilter): PathFinder =
		(this ** include) --- (this ** intermediateExclude ** include)
	
	/** Evaluates this finder.  The set returned by this method will reflect the underlying filesystem at the
	* time of calling.  If the filesystem changes, two calls to this method might be different.*/
	final def get: immutable.Set[Path] =
	{
		val pathSet = new mutable.HashSet[Path]
		addTo(pathSet)
		pathSet.toSet
	}
	/** Only keeps paths for which `f` returns true.  It is non-strict, so it is not evaluated until the returned finder is evaluated.*/
	final def filter(f: Path => Boolean): PathFinder = Path.lazyPathFinder(get.filter(f))
	/* Non-strict flatMap: no evaluation occurs until the returned finder is evaluated.*/
	final def flatMap(f: Path => PathFinder): PathFinder = Path.lazyPathFinder(get.flatMap(p => f(p).get))
	/** Evaluates this finder and converts the results to an `Array` of `URL`s..*/
	final def getURLs: Array[URL] = Path.getURLs(get)
	/** Evaluates this finder and converts the results to a `Set` of `File`s.*/
	final def getFiles: immutable.Set[File] = Path.getFiles(get)
	/** Evaluates this finder and converts the results to a `Set` of absolute path strings.*/
	final def getPaths: immutable.Set[String] = strictMap(_.absolutePath)
	/** Evaluates this finder and converts the results to a `Set` of relative path strings.*/
	final def getRelativePaths: immutable.Set[String] = strictMap(_.relativePath)
	final def strictMap[T](f: Path => T): immutable.Set[T] = get.map(f).toSet
	private[sbt] def addTo(pathSet: mutable.Set[Path])

	/** Create a PathFinder from this one where each path has a unique name.
	* A single path is arbitrarily selected from the set of paths with the same name.*/
	def distinct: PathFinder = Path.lazyPathFinder((Map() ++ get.map(p => (p.asFile.getName, p))) .values.toList )

	/** Constructs a string by evaluating this finder, converting the resulting Paths to absolute path strings, and joining them with the platform path separator.*/
	final def absString = Path.makeString(get)
	/** Constructs a string by evaluating this finder, converting the resulting Paths to relative path strings, and joining them with the platform path separator.*/
	final def relativeString = Path.makeRelativeString(get)
	/** Constructs a debugging string for this finder by evaluating it and separating paths by newlines.*/
	override def toString = get.mkString("\n   ", "\n   ","")
}
private class BasePathFinder(base: PathFinder) extends PathFinder
{
	private[sbt] def addTo(pathSet: mutable.Set[Path])
	{
		for(path <- base.get)
			pathSet += (path ###)
	}
}
private abstract class FilterPath extends PathFinder with FileFilter
{
	def parent: PathFinder
	def filter: FileFilter
	final def accept(file: File) = filter.accept(file)
	
	protected def handlePath(path: Path, pathSet: mutable.Set[Path])
	{
		for(matchedFile <- wrapNull(path.asFile.listFiles(this)))
			pathSet += path / matchedFile.getName
	}
}
private class DescendentOrSelfPathFinder(val parent: PathFinder, val filter: FileFilter) extends FilterPath
{
	private[sbt] def addTo(pathSet: mutable.Set[Path])
	{
		for(path <- parent.get)
		{
			if(accept(path.asFile))
				pathSet += path
			handlePathDescendent(path, pathSet)
		}
	}
	private def handlePathDescendent(path: Path, pathSet: mutable.Set[Path])
	{
		handlePath(path, pathSet)
		for(childDirectory <- wrapNull(path.asFile.listFiles(DirectoryFilter)))
			handlePathDescendent(path / childDirectory.getName, pathSet)
	}
}
private class ChildPathFinder(val parent: PathFinder, val filter: FileFilter) extends FilterPath
{
	private[sbt] def addTo(pathSet: mutable.Set[Path])
	{
		for(path <- parent.get)
			handlePath(path, pathSet)
	}
}
private class Paths(a: PathFinder, b: PathFinder) extends PathFinder
{
	private[sbt] def addTo(pathSet: mutable.Set[Path])
	{
		a.addTo(pathSet)
		b.addTo(pathSet)
	}
}
private class ExcludePaths(include: PathFinder, exclude: PathFinder) extends PathFinder
{
	private[sbt] def addTo(pathSet: mutable.Set[Path])
	{
		val includeSet = new mutable.HashSet[Path]
		include.addTo(includeSet)
		
		val excludeSet = new mutable.HashSet[Path]
		exclude.addTo(excludeSet)
		
		includeSet --= excludeSet
		pathSet ++= includeSet
	}
}