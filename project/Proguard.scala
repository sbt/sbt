	import sbt._
	import Keys._
	import Scope.{GlobalScope, ThisScope}

object LaunchProguard
{
	lazy val Proguard = config("proguard") hide ;

	lazy val configurationFile = SettingKey[File]("configuration-file")
	lazy val proguard = TaskKey[File]("proguard", "Produces the final compacted jar that contains only the classes needed using proguard.")
	lazy val proguardConfiguration = TaskKey[File]("proguard-configuration", "Creates the configuration file to use with proguard.")
	lazy val options = TaskKey[Seq[String]]("options")
	lazy val optimizePasses = SettingKey[Int]("optimize-passes")
	lazy val keepFullClasses = SettingKey[Seq[String]]("keep-full-classes")
	lazy val keepClasses = SettingKey[Seq[String]]("keep-classes")
	lazy val inputJars = TaskKey[Seq[File]]("input-jars")

	lazy val settings: Seq[Setting[_]] =
		inScope(GlobalScope)(inConfig(Proguard)(globalSettings)) ++
		inConfig(Proguard)( baseSettings ) :+
		(libraryDependencies += "net.sf.proguard" % "proguard-base" % "4.8" % Proguard.name)

	def globalSettings = Seq(
		optimizePasses := 2,
		keepFullClasses := Nil,
		keepClasses := Nil,
		options := basicOptions
	)
	def baseSettings = Seq(
		options <++= optimizePasses map { passes => if(passes <= 0) Seq("-dontoptimize") else Seq( "-optimizationpasses " + passes.toString, "-optimizations !code/allocation/variable") },
		options <++= keepFullClasses map { _ map ("-keep public class " + _  + " {\n\tpublic protected * ;\n}") },
		options <++= keepClasses map { _ map ("-keep class " + _  + " {}") },
		configurationFile <<= target / "proguard.pro",
		proguardConfiguration <<= writeProguardConfiguration,
		proguard <<= proguardTask
	)
	
	def specific(launchSub: Reference): Seq[Setting[_]] = inConfig(Proguard)(Seq(
		keepFullClasses ++= "xsbti.**" :: "jline.**" :: Nil,
		keepClasses ++= "org.apache.ivy.plugins.resolver.URLResolver" :: "org.apache.ivy.plugins.resolver.IBiblioResolver" :: Nil,
		artifactPath <<=  (target, version) { (out,v) => out / ("sbt-launch-" + v + ".jar") },
		options <++= (dependencyClasspath in (launchSub, Compile), compile in (launchSub,Compile), streams) map { (cp, analysis, s) => mapJars(cp.files, analysis.relations.allBinaryDeps.toSeq, s.log) },
		options <+= packageBin map { f => "-injars " + mkpath(f) },
		packageBin <<= (packageBin in (launchSub, Compile)).identity,
		options <++= mainClass in (launchSub, Compile) map { _.toList map(s => keepMain.format(s)) },
		options <+= artifactPath map { p => "-outjars " + mkpath(p) },
		fullClasspath <<= (configuration, classpathTypes, update) map Classpaths.managedJars
	))

	def mapJars(in: Seq[File], all: Seq[File], log: Logger): Seq[String] =
		mapInJars(in, log) ++ mapLibraryJars(all filterNot in.toSet)
	def writeProguardConfiguration = (options, configurationFile, streams) map { (opts, conf, s) =>
		val content = opts.mkString("\n")
		if(!conf.exists || IO.read(conf) != content) {
			s.log.info("Proguard configuration written to " + conf)
			IO.write(conf, content)
		}
		conf
	}

	def basicOptions =
		Seq(
			"-keep,allowoptimization,allowshrinking class * { *; }",
			"-keepattributes SourceFile,LineNumberTable",
			"-dontnote",
			"-dontwarn",
			 "-ignorewarnings")

	private val keepMain =
		"""-keep public class %s {
			|    public static void main(java.lang.String[]);
			|}""".stripMargin

	def mapLibraryJars(libraryJars: Seq[File]): Seq[String] = libraryJars.map(f => "-libraryjars " + mkpath(f))
	def mapOutJar(outJar: File) = "-outjars " + mkpath(outJar)

	def mkpath(f: File) : String = mkpath(f.getAbsolutePath, '\"')
	def mkpath(path: String, delimiter : Char) : String = delimiter + path + delimiter

	def proguardTask = (cacheDirectory, artifactPath, proguardConfiguration, fullClasspath, packageBin, streams) map { (cache, outputJar, configFile, cp, inJar, s) =>
		val f = FileFunction.cached(cache / "proguard", FilesInfo.hash) { _ =>
			runProguard(outputJar, configFile, cp.files, s.log)
			Set(outputJar)
		}
		f(Set(inJar, configFile)) // make the assumption that if the classpath changed, the outputJar would change
		outputJar
	}
	def runProguard(outputJar: File, configFile: File, cp: Seq[File], log: Logger)
	{
		IO.delete(outputJar)
		val fileString = mkpath(configFile.getAbsolutePath, '\'')
		val exitValue = Process("java", List("-Xmx256M", "-cp", Path.makeString(cp), "proguard.ProGuard", "-include " + fileString)) ! log
		if(exitValue != 0) error("Proguard failed with nonzero exit code (" + exitValue + ")")
	}

	def mapInJars(inJars: Seq[File], log: Logger): Seq[String] =
	{
		val (jlineJars, notJLine) = inJars partition isJarX("jline")
		val (ivyJars, notIvy) = notJLine partition isJarX("ivy")
		val (libraryJar, remaining) = notIvy partition isJarX("scala-library")
		val (compilerJar, otherJars) = remaining partition isJarX("scala-compiler")

		log.debug("proguard configuration:")
		log.debug("\tJLline jar location: " + jlineJars.mkString(", "))
		log.debug("\tIvy jar location: " + ivyJars.mkString(", "))
		log.debug("\tOther jars:\n\t" + otherJars.mkString("\n\t"))

		((withJar(ivyJars.toSeq, "Ivy") + excludeString(excludeIvyResources)) ::
		(withJar(jlineJars, "JLine") + jlineFilter ) ::
		(withJar(libraryJar, "Scala library") + libraryFilter) ::
		otherJars.map(jar => mkpath(jar) + generalFilter).toList) map { "-injars " + _ }
	}

	private def excludeString(s: List[String]) = s.map("!" + _).mkString("(",",",")")
	private def excludeIvyResources =
		"META-INF/**" ::
		"fr/**" ::
		"**/antlib.xml" ::
		"**/*.png" ::
		"org/apache/ivy/core/settings/ivyconf*.xml" ::
		"org/apache/ivy/core/settings/ivysettings-*.xml" ::
		"org/apache/ivy/plugins/resolver/packager/*" ::
		"**/ivy_vfs.xml" ::
		"org/apache/ivy/plugins/report/ivy-report-*" ::
		Nil

	private def libraryFilter = "(!META-INF/**,!*.properties,!scala/actors/**,!scala/util/parsing/*.class,**.class)"
	private def jlineFilter = "(!META-INF/**)"
	private def generalFilter = "(!META-INF/**,!*.properties)"

	private def withJar[T](files: Seq[File], name: String) = mkpath(files.headOption getOrElse error(name + " not present") )
	private def isJarX(x: String)(file: File) =
	{
		val name = file.getName
		name.startsWith(x) && name.endsWith(".jar")
	}
}
