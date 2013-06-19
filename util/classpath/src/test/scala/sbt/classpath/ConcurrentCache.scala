package sbt
package classpath

import org.scalacheck._
import Prop._
import java.io.File

object ConcurrentCache extends Properties("ClassLoaderCache concurrent access")
{
	implicit lazy val concurrentArb: Arbitrary[Int] = Arbitrary( Gen.choose(1, 1000) )
	implicit lazy val filenameArb: Arbitrary[String] = Arbitrary( Gen.alphaStr )

	property("Same class loader for same classpaths concurrently processed") = forAll { (names: List[String], concurrent: Int) =>
		withcp(names.distinct) { files =>
			val cache = new ClassLoaderCache(null)
			val loaders = (1 to concurrent).par.map(_ => cache(files)).toList
			sameClassLoader(loaders)
		}
	}

	private[this] def withcp[T](names: List[String])(f: List[File] => T): T = IO.withTemporaryDirectory { tmp =>
		val files = names.map{ name =>
			val file = new File(tmp, name)
			IO.touch(file)
			file
		}
		f(files)
	}
	private[this] def sameClassLoader(loaders: Seq[ClassLoader]): Boolean = loaders.size < 2 ||
		loaders.sliding(2).forall { case Seq(x,y) => x == y }
}