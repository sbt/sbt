package sbt

	import java.io.File
	import java.net.URL
	import Attributed.data
	import Build.analyzed
	import xsbt.api.{Discovered,Discovery}

object PluginDiscovery
{
	/** Relative paths of resources that list top-level modules that are available.
	* Normally, the classes for those modules will be in the same classpath entry as the resource. */
	object Paths
	{
		final val AutoPlugins = "sbt/sbt.autoplugins"
		final val Plugins = "sbt/sbt.plugins"
		final val Builds = "sbt/sbt.builds"
		final val AutoImports = "sbt/sbt.autoimports"
	}
	final class DiscoveredNames(val plugins: Seq[String], val autoImports: Seq[String], val autoPlugins: Seq[String], val builds: Seq[String])
	def emptyDiscoveredNames: DiscoveredNames = new DiscoveredNames(Nil, Nil, Nil, Nil)

	def discoverAll(data: PluginData, loader: ClassLoader): DetectedPlugins =
	{
		def discover[T](resource: String)(implicit mf: reflect.ClassManifest[T]) =
			binarySourceModules[T](data, loader, resource)
		import Paths._
		new DetectedPlugins(discover[Plugin](Plugins), discover[AutoImport](AutoImports), discover[AutoPlugin](AutoPlugins), discover[Build](Builds))
	}
	def discoverSourceAll(analysis: inc.Analysis): DiscoveredNames =
	{
		def discover[T](implicit mf: reflect.ClassManifest[T]): Seq[String] =
			sourceModuleNames(analysis, mf.erasure.getName)
		new DiscoveredNames(discover[Plugin], discover[AutoImport], discover[AutoPlugin], discover[Build])
	}

	// TODO: for 0.14.0, consider consolidating into a single file, which would make the classpath search 4x faster
	def writeDescriptors(names: DiscoveredNames, dir: File): Seq[File] =
	{
		import Paths._
		val files =
			writeDescriptor(names.plugins, dir, Plugins) ::
			writeDescriptor(names.autoPlugins, dir, AutoPlugins) ::
			writeDescriptor(names.builds, dir, Builds) ::
			writeDescriptor(names.autoImports, dir, AutoImports) ::
			Nil
		files.flatMap(_.toList)
	}

	def writeDescriptor(names: Seq[String], dir: File, path: String): Option[File] =
	{
		val descriptor: File = new File(dir, path)
		if(names.isEmpty)
		{
			IO.delete(descriptor)
			None
		}
		else
		{
			IO.writeLines(descriptor, names.distinct.sorted)
			Some(descriptor)
		}
	}


	def binarySourceModuleNames(classpath: Seq[Attributed[File]], loader: ClassLoader, resourceName: String, subclasses: String*): Seq[String] =
		(
			binaryModuleNames(data(classpath), loader, resourceName) ++
			(analyzed(classpath) flatMap ( a => sourceModuleNames(a, subclasses : _*) ))
		).distinct

	def sourceModuleNames(analysis: inc.Analysis, subclasses: String*): Seq[String] =
	{
		val subclassSet = subclasses.toSet
		val ds = Discovery(subclassSet, Set.empty)(Tests.allDefs(analysis))
		ds.flatMap {
			case (definition, Discovered(subs,_,_,true)) =>
				if((subs & subclassSet).isEmpty) Nil else definition.name :: Nil
			case _ => Nil
		}
	}

	def binaryModuleNames(classpath: Seq[File], loader: ClassLoader, resourceName: String): Seq[String] =
	{
		import collection.JavaConversions._
		loader.getResources(resourceName).toSeq.filter(onClasspath(classpath)) flatMap { u =>
			IO.readLinesURL(u).map( _.trim).filter(!_.isEmpty)
		}
	}
	def onClasspath(classpath: Seq[File])(url: URL): Boolean =
		IO.urlAsFile(url) exists (classpath.contains _)

	private[sbt] def binarySourceModules[T](data: PluginData, loader: ClassLoader, resourceName: String)(implicit mf: reflect.ClassManifest[T]): DetectedModules[T] =
	{
		val classpath = data.classpath
		val namesAndValues = if(classpath.isEmpty) Nil else {
			val names = binarySourceModuleNames(classpath, loader, resourceName, mf.erasure.getName)
			loadModules[T](data, names, loader)
		}
		new DetectedModules(namesAndValues)
	}

	private[this] def loadModules[T: ClassManifest](data: PluginData, names: Seq[String], loader: ClassLoader): Seq[(String,T)] =
		try ModuleUtilities.getCheckedObjects[T](names, loader)
		catch {
			case e: ExceptionInInitializerError =>
				val cause = e.getCause
				if(cause eq null) throw e else throw cause
			case e: LinkageError => incompatiblePlugins(data, e)
		}

	private[this] def incompatiblePlugins(data: PluginData, t: LinkageError): Nothing =
	{
		val evicted = data.report.toList.flatMap(_.configurations.flatMap(_.evicted))
		val evictedModules = evicted map { id => (id.organization, id.name) } distinct ;
		val evictedStrings = evictedModules map { case (o,n) => o + ":" + n }
		val msgBase = "Binary incompatibility in plugins detected."
		val msgExtra = if(evictedStrings.isEmpty) "" else "\nNote that conflicts were resolved for some dependencies:\n\t" + evictedStrings.mkString("\n\t")
		throw new IncompatiblePluginsException(msgBase + msgExtra, t)
	}
}