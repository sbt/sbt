object ForkTest {
  def main(args:Array[String]): Unit = {
		val name = Option(System.getenv("flag.name")) getOrElse("flag")
		println("Name: " + name)
		val cwd = (new java.io.File(name)).getAbsoluteFile
		cwd.getParentFile.mkdirs()
		cwd.createNewFile()
  }
}
