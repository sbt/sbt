object A {
	def main(args: Array[String]) =
	{
		assert(args(0).toInt == args(1).toInt)
		assert(java.lang.Boolean.getBoolean("sbt.check.forked"))
	}
}
