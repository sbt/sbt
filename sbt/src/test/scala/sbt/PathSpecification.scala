/* sbt -- Simple Build Tool
 * Copyright 2008 Mark Harrah
 */
package sbt

import org.scalacheck._
import Arbitrary.arbitrary
import Prop._
import java.io.File

object PathSpecification extends Properties("Path")
{
	val log = new ConsoleLogger
	log.setLevel(Level.Warn)

	// certain operations require a real underlying file.  We'd like to create them in a managed temporary directory so that junk isn't left over from the test.
	//  The arguments to several properties are functions that construct a Path or PathFinder given a base directory.
	type ToPath = ProjectDirectory => Path
	type ToFinder = ProjectDirectory => PathFinder
	
	implicit val pathComponent: Arbitrary[String] =
		Arbitrary(for(id <- Gen.identifier) yield trim(id)) // TODO: make a more specific Arbitrary
	implicit val arbComponents: Arbitrary[List[String]] = Arbitrary(componentList)
	implicit val arbDup: Arbitrary[(String, Int)] = Arbitrary.arbTuple2(pathComponent, Arbitrary(Gen.choose(0, MaxDuplicates)))
	implicit val arbPath: Arbitrary[ToPath] = Arbitrary(genPath)
	implicit val arbDirs: Arbitrary[ToFinder] = Arbitrary(directories)
	
	property("Project directory relative path empty") = secure { inTemp { dir => dir.relativePath.isEmpty } }
	property("construction") = forAll { (components: List[String]) =>
		inTemp { dir =>
			pathForComponents(dir, components).asFile == fileForComponents(dir.asFile, components)
		}
	}
	property("Relative path") = forAll { (a: List[String], b: List[String]) =>
		inTemp { dir =>
			pathForComponents(pathForComponents(dir, a) ##, b).relativePath == pathString(b) }
		}
	property("Proper URL conversion") = forAll { (tp: ToPath) =>
		withPath(tp) { path => path.asURL == path.asFile.toURI.toURL }
	}
	property("Path equality") = forAll { (components: List[String]) =>
		inTemp { dir => pathForComponents(dir, components) == pathForComponents(dir, components) }
	}
	property("Base path equality") = forAll { (a: List[String], b: List[String]) =>
		inTemp { dir =>
			pathForComponents(pathForComponents(dir, a) ##, b) == pathForComponents(pathForComponents(dir, a) ##, b)
		}
	}

	property("hashCode") = forAll { (tp: ToPath) =>
		withPath(tp) { path =>
			path.hashCode == path.asFile.hashCode
		}
	}

	property("relativize fail") = forAll { (a: List[String], b: List[String]) =>
		createFileAndDo(a, b)
		{ dir =>
			{
				val shouldFail = (a == b) || !(b startsWith a) // will be true most of the time
				val didFail = Path.relativize(pathForComponents(dir, a), pathForComponents(dir, b)).isEmpty
				shouldFail == didFail
			}
		}
	}
	property("relativize") = forAll {(a: List[String], b: List[String]) =>
		(!b.isEmpty) ==>
		{
			createFileAndDo(a, b)
			{ dir =>
				{
					val base = pathForComponents(dir, a)
					val path = pathForComponents(base, b)
					Path.relativize(base, path) == Some(path)
				}
			}
		}
	}
	property("fromString") = forAll { (a: List[String]) =>
		inTemp { dir =>
			pathForComponents(dir, a) == Path.fromString(dir, pathString(a))
		}
	}

	property("distinct") = forAll { (baseDirs: ToFinder, distinctNames: List[String], dupNames: List[(String, Int)]) => try {
		inTemp { dir =>
			val bases = repeat(baseDirs(dir).get)
			val reallyDistinct: Set[String] = Set() ++ distinctNames -- dupNames.map(_._1)
			val dupList = dupNames.flatMap { case (name, repeat) => if(reallyDistinct(name)) Nil else List.make(repeat, name) }

			def create(names: List[String]): PathFinder =
			{
				val paths = (bases zip names ).map { case (a, b) => a / b }.filter(!_.exists)
				paths.foreach { f => xsbt.FileUtilities.touch(f asFile) }
				Path.lazyPathFinder(paths)
			}
			def names(s: scala.collection.Set[Path]) = s.map(_.name)

			val distinctPaths = create(reallyDistinct.toList)
			val dupPaths = create(dupList)

			val all = distinctPaths +++ dupPaths
			val distinct = all.distinct.get

			val allNames = Set() ++ names(all.get)

			(Set() ++ names(distinct)) == allNames && // verify nothing lost
				distinct.size == allNames.size // verify duplicates removed
		} } catch { case e => e.printStackTrace; throw e}
	}

	private def repeat[T](s: Iterable[T]): List[T] =
		List.make(100, ()).flatMap(_ => s) // should be an infinite Stream, but Stream isn't very lazy
		

	private def withPath[T](tp: ToPath)(f: Path => T): T =
		inTemp { f compose tp }
	private def withPaths[T](ta: ToPath, tb: ToPath)(f: (Path, Path) => T): T =
		inTemp { dir => f(ta(dir), tb(dir)) }
	
	private def createFileAndDo(a: List[String], b: List[String])(f: Path => Boolean) =
	{
		val result =
			FileUtilities.doInTemporaryDirectory(log)( dir =>
			{
				FileUtilities.touch(fileForComponents(dir, a ::: b), log) match
				{
					case None => Right(Some( f(new ProjectDirectory(dir)) ))
					case Some(err) => Left(err)
				}
			})
		result match
		{
			case Left(err) => throw new RuntimeException(err)
			case Right(opt) => opt.isDefined ==> opt.get
		}
	}
	private def inTemp[T](f: ProjectDirectory => T): T =
		xsbt.FileUtilities.withTemporaryDirectory { dir => f(new ProjectDirectory(dir)) }
	
	private def pathString(components: List[String]): String = components.mkString(File.separator)
	private def pathForComponents(base: Path, components: List[String]): Path =
		components.filter(!_.isEmpty).foldLeft(base)((path, component) => path / component)
	private def fileForComponents(base: File, components: List[String]): File =
		components.foldLeft(base)((file, component) => new File(file, component))

	private def paths(implicit d: Gen[ToPath], s: Gen[String]): Gen[ToPath] =
		for(dir <- d; name <- s) yield  {
			(projectPath: ProjectDirectory) =>
				val f = dir(projectPath) / name
				xsbt.FileUtilities.touch(f asFile)
				f
		}

	private def directories: Gen[ToFinder] =
		for(dirs <- directoryList) yield {
			(projectPath: ProjectDirectory) => Path.lazyPathFinder { dirs.map(_(projectPath)) }
		}
	private def directoryList: Gen[List[ToPath]] = genList(MaxDirectoryCount)(directory)
	private def directory: Gen[ToPath] =
		for(p <- genPath) yield {
			(projectPath: ProjectDirectory) => {
				val f = p(projectPath)
				xsbt.FileUtilities.createDirectory(f asFile)
				f
			}
		}

	private implicit lazy val genPath: Gen[ToPath] =
		for(a <- arbitrary[List[String]];
			b <- arbitrary[Option[List[String]]])
		yield
			(projectPath: ProjectDirectory) =>
			{
				val base = pathForComponents(projectPath, a)
				b match
				{
					case None => base
					case Some(relative) => pathForComponents(base ##, relative)
				}
			}
	private implicit lazy val componentList: Gen[List[String]] = genList[String](MaxComponentCount)(pathComponent.arbitrary)

	private def genList[A](maxSize: Int)(implicit genA: Gen[A]) =
		for(size <- Gen.choose(0, maxSize); a <- Gen.listOfN(size, genA)) yield a

	private def trim(components: List[String]): List[String] = components.take(MaxComponentCount)
	private def trim(component: String): String = component.substring(0, Math.min(component.length, MaxFilenameLength))

	final val MaxFilenameLength = 20
	final val MaxComponentCount = 6
	final val MaxDirectoryCount = 10
	final val MaxFilesCount = 100
	final val MaxDuplicates = 10
}