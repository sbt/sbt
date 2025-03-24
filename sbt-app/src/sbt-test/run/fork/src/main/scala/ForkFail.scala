object ForkTest {
  def main(args:Array[String]): Unit = {
		val name = sys.env.getOrElse("flag.name", "flag")
		println("Name: " + name)
		val cwd = (new java.io.File(name)).getAbsoluteFile
		cwd.getParentFile.mkdirs()
		cwd.createNewFile()
  }
}
