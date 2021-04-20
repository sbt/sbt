object Counter {
	private[this] val Name = "test.count"

	// synchronize on Predef because that is shared between the subprojects
	def get = Predef.synchronized { System.getProperty(Name, "0").toInt }

	def add(i: Int) = Predef.synchronized {
		val count = get + i
		System.setProperty(Name, count.toString)
		count
	}
}