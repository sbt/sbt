package xsbt.boot

import org.scalacheck._
import Prop._
import java.io.File

object LocksTest extends Properties("Locks")
{
	property("Lock in nonexisting directory") = 
		FileUtilities.withTemporaryDirectory { dir =>
			val lockFile = new File(dir, "doesntexist/lock")
			Locks(lockFile, new java.util.concurrent.Callable[Boolean] { def call = true })
		}
}