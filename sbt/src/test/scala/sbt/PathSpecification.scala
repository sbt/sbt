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
	
	implicit val pathComponent: Arbitrary[String] =
		Arbitrary(for(id <- Gen.identifier) yield trim(id)) // TODO: make a more specific Arbitrary
	implicit val projectDirectory: Arbitrary[ProjectDirectory] = Arbitrary(Gen.value(new ProjectDirectory(new File("."))))
	implicit val arbPath: Arbitrary[Path] = Arbitrary(genPath)
	
	specify("Project directory relative path empty", (projectPath: ProjectDirectory) => projectPath.relativePath.isEmpty)
	specify("construction", (dir: ProjectDirectory, components: List[String]) =>
		pathForComponents(dir, components).asFile == fileForComponents(dir.asFile, components) )
	specify("Relative path", (dir: ProjectDirectory, a: List[String], b: List[String]) =>
		pathForComponents(pathForComponents(dir, a) ##, b).relativePath == pathString(b) )
	specify("Proper URL conversion", (path: Path) => path.asURL == path.asFile.toURI.toURL)
	specify("Path equality", (dir: ProjectDirectory, components: List[String]) =>
		pathForComponents(dir, components) == pathForComponents(dir, components))
	specify("Base path equality", (dir: ProjectDirectory, a: List[String], b: List[String]) =>
		pathForComponents(pathForComponents(dir, a) ##, b) == pathForComponents(pathForComponents(dir, a) ##, b) )
	specify("hashCode", (path: Path) => path.hashCode == path.asFile.hashCode)
	
	// the relativize tests are a bit of a mess because of a few things:
	//  1) relativization requires directories to exist
	//  2) there is an IOException thrown in touch for paths that are too long (probably should limit the size of the Lists)
	// These problems are addressed by the helper method createFileAndDo
	
	specify("relativize fail", (dir: ProjectDirectory, a: List[String], b: List[String]) =>
	{
		(!a.contains("") && !b.contains("")) ==>
		{
			createFileAndDo(a, b)
			{ dir =>
				{
					val shouldFail = (a == b) || !(b startsWith a) // will be true most of the time
					val didFail = Path.relativize(pathForComponents(dir, a), pathForComponents(dir, b)).isEmpty
					shouldFail == didFail
				}
			}
		}
	})
	specify("relativize", (a: List[String], b: List[String]) =>
	{
		(!b.isEmpty && !a.contains("") && !b.contains("")) ==>
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
	})
	specify("fromString", (dir: ProjectDirectory, a: List[String]) =>
		pathForComponents(dir, a) == Path.fromString(dir, pathString(a)))
	
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
	
	private def pathString(components: List[String]): String = components.mkString(File.separator)
	private def pathForComponents(base: Path, components: List[String]): Path =
		components.foldLeft(base)((path, component) => path / component)
	private def fileForComponents(base: File, components: List[String]): File =
		components.foldLeft(base)((file, component) => new File(file, component))
	private def genPath: Gen[Path] =
		for(projectPath <- arbitrary[ProjectDirectory];
			a <- arbitrary[List[String]];
			b <- arbitrary[Option[List[String]]])
		yield
		{
			val base = pathForComponents(projectPath, trim(a))
			b match
			{
				case None => base
				case Some(relative) => pathForComponents(base ##, trim(relative))
			}
		}
	private def trim(components: List[String]): List[String] = components.take(MaxComponentCount)
	private def trim(component: String): String = component.substring(0, Math.min(component.length, MaxFilenameLength))
	val MaxFilenameLength = 20
	val MaxComponentCount = 6
}