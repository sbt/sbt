package sbt

// minimal copy of scala.concurrent.SyncVar since that version deprecated put and unset
private[sbt] final class SyncVar[A]
{
	private[this] var isDefined: Boolean = false
	private[this] var value: Option[A] = None

	/** Waits until a value is set and then gets it.  Does not clear the value */
	def get: A = synchronized {
		while (!isDefined) wait()
		value.get
	}

	/** Waits until a value is set, gets it, and finally clears the value. */
	def take(): A = synchronized {
		try get finally unset()
	}

	/** Sets the value, whether or not it is currently defined. */
	def set(x: A): Unit = synchronized {
 		isDefined = true
		value = Some(x)
		notifyAll()
	}

	/** Sets the value, first waiting until it is undefined if it is currently defined. */
	def put(x: A): Unit = synchronized {
		while (isDefined) wait()
		set(x)
	}

	/** Clears the value, whether or not it is current defined. */
	def unset(): Unit = synchronized {
		isDefined = false
		value = None
		notifyAll()
	}
}

