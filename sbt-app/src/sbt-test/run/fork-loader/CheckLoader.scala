object CheckLoader {
	def main(args: Array[String]): Unit = apply()
	def apply(): Unit = {
		val loader = getClass.getClassLoader
		val appLoader = ClassLoader.getSystemClassLoader
		assert(loader eq appLoader, "Application classes not loaded in the system class loader")
	}
}
