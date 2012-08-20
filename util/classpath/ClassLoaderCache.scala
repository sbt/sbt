package sbt.classpath

import java.lang.ref.{Reference, SoftReference}
import java.io.File
import java.net.URLClassLoader
import java.util.HashMap

private[sbt] final class ClassLoaderCache(val commonParent: ClassLoader)
{
	private[this] val delegate = new HashMap[List[File],Reference[CachedClassLoader]]
	def apply(files: List[File]): ClassLoader =
	{
		val tstamps = files.map(_.lastModified)
		getFromReference(files, tstamps, delegate.get(files))
	}
	
	private[this] def getFromReference(files: List[File], stamps: List[Long], existingRef: Reference[CachedClassLoader]) =
		if(existingRef eq null)
			newEntry(files, stamps)
		else
			get(files, stamps, existingRef.get)

	private[this] def get(files: List[File], stamps: List[Long], existing: CachedClassLoader): ClassLoader =
		if(existing == null || stamps != existing.timestamps)
			newEntry(files, stamps)
		else
			existing.loader

	private[this] def newEntry(files: List[File], stamps: List[Long]): ClassLoader =
	{
		val loader = new URLClassLoader(files.map(_.toURI.toURL).toArray, commonParent)
		delegate.put(files, new SoftReference(new CachedClassLoader(loader, files, stamps)))
		loader
	}
}
private[sbt] final class CachedClassLoader(val loader: ClassLoader, val files: List[File], val timestamps: List[Long])
