object ForkTest {
  def main(args:Array[String]) {
		val cwd = (new java.io.File("flag")).getAbsoluteFile
		cwd.getParentFile.mkdirs()
		cwd.createNewFile()
  }
}
