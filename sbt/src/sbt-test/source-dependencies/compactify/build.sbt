TaskKey("output-empty") <<= ClassDirectory in Configurations.Compile map { outputDirectory =>
	def classes = (outputDirectory ** "*.class").get
	if(!classes.isEmpty) error("Classes existed:\n\t" + classes.mkString("\n\t"))
}
