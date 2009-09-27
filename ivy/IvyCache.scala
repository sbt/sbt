package xsbt

import java.io.File
import java.net.URL

import org.apache.ivy.{core, plugins, util}
import core.cache.{ArtifactOrigin, CacheDownloadOptions, DefaultRepositoryCacheManager}
import core.module.descriptor.{Artifact => IvyArtifact, DefaultArtifact}
import plugins.repository.file.{FileRepository=>IvyFileRepository, FileResource}
import plugins.repository.{ArtifactResourceResolver, Resource, ResourceDownloader}
import plugins.resolver.util.ResolvedResource
import util.FileUtil

class NotInCache(val id: ModuleID, cause: Throwable)
	extends RuntimeException(NotInCache(id, cause), cause)
{
	def this(id: ModuleID) = this(id, null)
}
private object NotInCache
{
	def apply(id: ModuleID, cause: Throwable) =
	{
		val postfix = if(cause == null) "" else (": " +cause.toString)
		"File for " + id + " not in cache" + postfix
	}
}
/** Provides methods for working at the level of a single jar file with the default Ivy cache.*/
object IvyCache
{
	/** Caches the given 'file' with the given ID.  It may be retrieved or cleared using this ID.*/
	def cacheJar(moduleID: ModuleID, file: File, log: IvyLogger)
	{
		val artifact = defaultArtifact(moduleID)
		val resolved = new ResolvedResource(new FileResource(new IvyFileRepository, file), moduleID.revision)
		withDefaultCache(log) { cache =>
			val resolver = new ArtifactResourceResolver { def resolve(artifact: IvyArtifact) = resolved }
			cache.download(artifact, resolver, new FileDownloader, new CacheDownloadOptions)
		}
	}
	/** Clears the cache of the jar for the given ID.*/
	def clearCachedJar(id: ModuleID, log: IvyLogger)
	{
		try { withCachedJar(id, log)(_.delete) }
		catch { case e: Exception => log.debug("Error cleaning cached jar: " + e.toString) }
	}
	/** Copies the cached jar for the given ID to the directory 'toDirectory'.  If the jar is not in the cache, NotInCache is thrown.*/
	def retrieveCachedJar(id: ModuleID, toDirectory: File, log: IvyLogger) =
		withCachedJar(id, log) { cachedFile =>
			val copyTo = new File(toDirectory, cachedFile.getName)
			FileUtil.copy(cachedFile, copyTo, null)
			copyTo
		}

	/** Get the location of the cached jar for the given ID in the Ivy cache.  If the jar is not in the cache, NotInCache is thrown
	* TODO: locking.*/
	def withCachedJar[T](id: ModuleID, log: IvyLogger)(f: File => T): T =
	{
		val cachedFile =
			try
			{
				withDefaultCache(log) { cache =>
					val artifact = defaultArtifact(id)
					cache.getArchiveFileInCache(artifact, unknownOrigin(artifact))
				}
			}
			catch { case e: Exception => throw new NotInCache(id, e) }

		if(cachedFile.exists) f(cachedFile) else throw new NotInCache(id)
	}
	/** Calls the given function with the default Ivy cache.*/
	def withDefaultCache[T](log: IvyLogger)(f: DefaultRepositoryCacheManager => T): T =
	{
		val (ivy, local) = basicLocalIvy(log)
		ivy.withIvy { ivy =>
			val cache = ivy.getSettings.getDefaultRepositoryCacheManager.asInstanceOf[DefaultRepositoryCacheManager]
			cache.setUseOrigin(false)
			f(cache)
		}
	}
	private def unknownOrigin(artifact: IvyArtifact) = ArtifactOrigin.unkwnown(artifact)
	/** A minimal Ivy setup with only a local resolver and the current directory as the base directory.*/
	private def basicLocalIvy(log: IvyLogger) =
	{
		val local = Resolver.defaultLocal
		val paths = new IvyPaths(new File("."), None)
		val conf = new IvyConfiguration(paths, Seq(local), Nil, log)
		(new IvySbt(conf), local)
	}
	/** Creates a default jar artifact based on the given ID.*/
	private def defaultArtifact(moduleID: ModuleID): IvyArtifact =
		new DefaultArtifact(IvySbt.toID(moduleID), null, moduleID.name, "jar", "jar")
}
/** Required by Ivy for copying to the cache.*/
private class FileDownloader extends ResourceDownloader with NotNull
{
	def download(artifact: IvyArtifact, resource: Resource, dest: File)
	{
		if(dest.exists()) dest.delete()
		val part = new File(dest.getAbsolutePath + ".part")
		FileUtil.copy(resource.openStream, part, null)
		if(!part.renameTo(dest))
			error("Could not move temporary file " + part + " to final location " + dest)
	}
}