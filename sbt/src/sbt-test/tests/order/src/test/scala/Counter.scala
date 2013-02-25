object Counter {
	private[this] var iv = 0
	def i: Int = synchronized { iv }
	def setI(value: Int): Unit = synchronized { iv = value }
}