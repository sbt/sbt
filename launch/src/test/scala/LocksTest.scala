package xsbt.boot

import org.scalacheck._
import Prop._
import java.io.File

/** These mainly test that things work in the uncontested case and that no OverlappingFileLockExceptions occur.
* There is no real locking testing, just the coordination of locking.*/
object LocksTest extends Properties("Locks")
{
	property("Lock in nonexisting directory") = spec {
		FileUtilities.withTemporaryDirectory { dir =>
			val lockFile = new File(dir, "doesntexist/lock")
			Locks(lockFile, callTrue)
		}
	}
		
	property("Uncontested re-entrant lock") =  spec {
		FileUtilities.withTemporaryDirectory { dir =>
			val lockFile = new File(dir, "lock")
			Locks(lockFile,  callLocked(lockFile)) &&
			Locks(lockFile,  callLocked(lockFile))
		}
	}
		
	property("Uncontested double lock") = spec {
		FileUtilities.withTemporaryDirectory { dir =>
			val lockFileA = new File(dir, "lockA")
			val lockFileB = new File(dir, "lockB")
			Locks(lockFileA,  callLocked(lockFileB)) &&
			Locks(lockFileB,  callLocked(lockFileA))
		}
	}
		
	property("Contested single lock") = spec {
		FileUtilities.withTemporaryDirectory { dir =>
			val lockFile = new File(dir, "lock")
			forkFold(2000){i => Locks(lockFile, callTrue) }
		}
	}

	private def spec(f: => Boolean): Prop = Prop { _ => Result(if(f) True else False) }
		
	private def call[T](impl: => T) = new java.util.concurrent.Callable[T] { def call = impl }
	private def callLocked(lockFile: File) = call { Locks(lockFile, callTrue) }
	private lazy val callTrue =  call { true }
	
	private def forkFold(n: Int)(impl: Int => Boolean): Boolean =
		(true /: forkWait(n)(impl))(_ && _)
	private def forkWait(n: Int)(impl: Int  => Boolean): Iterable[Boolean] =
	{
		import scala.concurrent.ops.future
		val futures = (0 until n).map { i => future { impl(i) } }
		futures.toList.map(_())
	}
}