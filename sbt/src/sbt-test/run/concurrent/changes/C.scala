import java.io.File

object C {
	def main(args: Array[String]): Unit = {
		val base = new File(args(0))
		create(new File(base, "started"))
		val bFin = new File(base, "../b/finished")
		waitFor(bFin)
		create(new File(base, "finished"))
	}

	def create(f: File): Unit = {
		val fabs = f.getAbsoluteFile
		fabs.getParentFile.mkdirs
		fabs.createNewFile
	}

	def waitFor(f: File): Unit = {
		if(!f.exists) {
			Thread.sleep(300)
			waitFor(f)
		}
	}
}
