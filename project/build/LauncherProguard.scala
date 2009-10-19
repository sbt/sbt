import sbt._
import java.io.File

trait ProguardLaunch extends ProguardProject
{
	override def basicOptions = super.basicOptions ++ Seq(keepJLine)
	def outputJar = rootProject.outputPath / ("xsbt-launch-" + version + ".jar")
	override def keepClasses =
		"org.apache.ivy.plugins.resolver.URLResolver" ::
		"org.apache.ivy.plugins.resolver.IBiblioResolver" ::
		"xsbti.**" ::
		Nil
	override def mapInJars(inJars: Seq[File]) =
	{
		val inputJar = jarPath.asFile.getAbsolutePath
		val runtimeClasspath = runClasspath.get.map(_.asFile).toList
		val jlineJars = runtimeClasspath.filter(isJLineJar)
		// pull out Ivy in order to exclude resources inside
		val (ivyJars, notIvy) = inJars.partition(_.getName.startsWith("ivy"))
		val otherJars = notIvy.filter(jar => !isJarX(jar, "scala-compiler"))

		log.debug("proguard configuration ivy jar location: " + ivyJars.mkString(", "))

		val excludeIvyResourcesString = excludeString(excludeIvyResources)
		((withJar(ivyJars.toSeq, "Ivy") + excludeIvyResourcesString) ::
		(withJar(jlineJars, "JLine") + "(!META-INF/**)" ) ::
		otherJars.map(jar => mkpath(jar) + "(!META-INF/**,!*.properties)").toList) map { "-injars " + _ }
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

	private def withJar[T](files: Seq[File], name: String) = mkpath(files.firstOption.getOrElse(error(name + " not present (try running update)")))
	private def isJLineJar(file: File) = isJarX(file, "jline")
	private def isJarX(file: File, x: String) =
	{
		val name = file.getName
		name.startsWith(x) && name.endsWith(".jar")
	}
	// class body declaration for proguard that keeps all public members
	private val allPublic = " {\n public * ;\n}"

	private val keepJLine =
	"""
	|-keep public class jline.** {
	|    public protected *;
	|}
	|""".stripMargin
}
