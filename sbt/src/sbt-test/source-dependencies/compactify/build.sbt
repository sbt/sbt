TaskKey[Unit]("output-empty") <<= classDirectory in Configurations.Compile map { outputDirectory =>
	def classes = (outputDirectory ** "*.class").get
	if(!classes.isEmpty) error("Classes existed:\n\t" + classes.mkString("\n\t")) else ()
}
