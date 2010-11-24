/* sbt -- Simple Build Tool
 * Copyright 2010  Mark Harrah
 */
package sbt

	import java.io.File

/** 
Maintains a set of mappings so that they are uptodate.
Specifically, 'apply' applies the mappings by creating target directories and copying source files to their destination.
For each mapping no longer present, the old target is removed.
Caution: Existing files are overwritten.
Caution: The removal of old targets assumes that nothing else has written to or modified those files.
  It tries not to obliterate large amounts of data by only removing previously tracked files and empty directories.
  That is, it won't remove a directory with unknown (untracked) files in it.
Warning: It is therefore inappropriate to use this with anything other than an automatically managed destination or a dedicated target directory.
Warning: Specifically, don't mix this with a directory containing manually created files, like sources.
It is safe to use for its intended purpose: copying resources to a class output directory.
*/
object Sync
{
	def apply(cacheFile: File, inStyle: FileInfo.Style = FileInfo.lastModified, outStyle: FileInfo.Style = FileInfo.exists): Iterable[(File,File)] => Relation[File,File] =
		mappings =>
		{
			val relation = Relation.empty ++ mappings
			noDuplicateTargets(relation)
			val currentInfo = relation._1s.map(s => (s, inStyle(s)) ).toMap

			val (previousRelation, previousInfo) = readInfo(cacheFile)(inStyle.format)
			val removeTargets = previousRelation._2s -- relation._2s

			def outofdate(source: File, target: File): Boolean =
				!previousRelation.contains(source, target) ||
				(previousInfo get source) != (currentInfo get source) ||
				!target.exists ||
				target.isDirectory != source.isDirectory

			val updates = relation filter outofdate

			val (cleanDirs, cleanFiles) = (updates._2s ++ removeTargets).partition(_.isDirectory)

			IO.delete(cleanFiles)
			IO.deleteIfEmpty(cleanDirs)
			updates.all.foreach((copy _).tupled)

			writeInfo(cacheFile, relation, currentInfo)(inStyle.format)
			relation
		}
		
	def copy(source: File, target: File): Unit =
		if(source.isFile)
			IO.copyFile(source, target, true)
		else if(!target.exists) // we don't want to update the last modified time of an existing directory
		{
			IO.createDirectory(target)
			IO.copyLastModified(source, target)
		}

	def noDuplicateTargets(relation: Relation[File, File])
	{
		val dups = relation.reverseMap.collect {
			case (target, srcs) if srcs.size >= 2 =>
				"\n\t" + target + "\nfrom\n\t" + srcs.mkString("\n\t")
		}
		if(!dups.isEmpty)
			error("Duplicate mappings:" + dups.mkString)
	}

		
		import java.io.{File, IOException}
		import sbinary._
		import Operations.{read, write}
		import DefaultProtocol.{FileFormat => _, _}
		import JavaIO._
		import inc.AnalysisFormats._

	def writeInfo[F <: FileInfo](file: File, relation: Relation[File, File], info: Map[File, F])(implicit infoFormat: Format[F]): Unit =
		IO.gzipFileOut(file) { out =>
			write(out, (relation, info) )
		}

	type RelationInfo[F] = (Relation[File,File], Map[File, F])

	def readInfo[F <: FileInfo](file: File)(implicit infoFormat: Format[F]): RelationInfo[F] =
		try { readUncaught(file)(infoFormat) }
		catch { case e: IOException => (Relation.empty, Map.empty) }

	def readUncaught[F <: FileInfo](file: File)(implicit infoFormat: Format[F]): RelationInfo[F] =
		IO.gzipFileIn(file) { in =>
			read[RelationInfo[F]](in)
		}
}