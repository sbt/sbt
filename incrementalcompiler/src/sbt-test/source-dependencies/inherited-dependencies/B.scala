object B {
	type T[x] = C
}

class B {
	// not public, so this shouldn't be tracked as an inherited dependency
	private[this] class X extends D with E[Int]

	def x(i: Int): Unit = {
		// not public, not an inherited dependency
		trait Y extends D
	}

	def y(j: Int): Unit = {
		// not public
		val w: D { def length: Int } = ???
		()
	}
}
