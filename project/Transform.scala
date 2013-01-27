	import sbt._
	import Keys._

object Transform
{
	lazy val transformSources = TaskKey[Seq[File]]("transform-sources")
	lazy val inputSourceDirectories = SettingKey[Seq[File]]("input-source-directories")
	lazy val inputSourceDirectory = SettingKey[File]("input-source-directory")
	lazy val inputSources = TaskKey[Seq[File]]("input-sources")
	lazy val sourceProperties = TaskKey[Map[String,String]]("source-properties")

	lazy val transformResources = TaskKey[Seq[File]]("transform-resources")
	lazy val inputResourceDirectories = SettingKey[Seq[File]]("input-resource-directories")
	lazy val inputResourceDirectory = SettingKey[File]("input-resource-directory")
	lazy val inputResources = TaskKey[Seq[File]]("input-resources")
	lazy val resourceProperties = TaskKey[Map[String,String]]("resource-properties")
	// to be replace by 0.10.1's fileMappings
	lazy val fileMappings = TaskKey[Seq[(File,File)]]("file-mappings")

	def crossGenSettings = transSourceSettings ++ seq(
		sourceProperties := Map("cross.package0" -> "sbt", "cross.package1" -> "cross")
	)
	def transSourceSettings = seq(
		inputSourceDirectory <<= sourceDirectory / "input_sources",
		inputSourceDirectories <<= Seq(inputSourceDirectory).join,
		inputSources <<= inputSourceDirectories.map(dirs => (dirs ** (-DirectoryFilter)).get ),
		fileMappings in transformSources <<= transformSourceMappings,
		transformSources <<= (fileMappings in transformSources, sourceProperties) map { (rs, props) =>
			rs map { case (in, out) => transform(in, out, props) }
		},
		sourceGenerators <+= transformSources
	)
	def transformSourceMappings = (inputSources, inputSourceDirectories, sourceManaged) map { (ss, sdirs, sm) =>
		(ss --- sdirs) x (rebase(sdirs, sm)|flat(sm)) toSeq
	}
	def configSettings = transResourceSettings ++ seq(
		resourceProperties <<= (organization, version, scalaVersion, Status.isSnapshot) map { (org, v, sv, isSnapshot) =>
			Map("org" -> org, "sbt.version" -> v, "scala.version" -> sv, "repositories" -> repositories(isSnapshot).mkString(IO.Newline))
		}
	)
	def transResourceSettings = seq(
		inputResourceDirectory <<= sourceDirectory / "input_resources",
		inputResourceDirectories <<= Seq(inputResourceDirectory).join,
		inputResources <<= inputResourceDirectories.map(dirs => (dirs ** (-DirectoryFilter)).get ),
		fileMappings in transformResources <<= transformResourceMappings,
		transformResources <<= (fileMappings in transformResources, resourceProperties) map { (rs, props) =>
			rs map { case (in, out) => transform(in, out, props) }
		},
		resourceGenerators <+= transformResources
	)
	def transformResourceMappings = (inputResources, inputResourceDirectories, resourceManaged) map { (rs, rdirs, rm) =>
		(rs --- rdirs) x (rebase(rdirs, rm)|flat(rm)) toSeq
	}

	def transform(in: File, out: File, map: Map[String, String]): File =
	{
		def get(key: String): String = map.getOrElse(key, error("No value defined for key '" + key + "'"))
		val newString = Property.replaceAllIn(IO.read(in), mtch => get(mtch.group(1)) )
		if(Some(newString) != read(out))
			IO.write(out, newString)
		out
	}
	def read(file: File): Option[String] = try { Some(IO.read(file)) } catch { case _: java.io.IOException => None }
	lazy val Property = """\$\{\{([\w.-]+)\}\}""".r

	def repositories(isSnapshot: Boolean) = Releases :: (if(isSnapshot) Snapshots :: Nil else Nil)
	lazy val Releases = typesafeRepository("releases")
	lazy val Snapshots = typesafeRepository("snapshots")
	def typesafeRepository(status: String) =
		"""  typesafe-ivy-%s: http://repo.typesafe.com/typesafe/ivy-%<s/, [organization]/[module]/[revision]/[type]s/[artifact](-[classifier]).[ext], bootOnly""" format status
}
