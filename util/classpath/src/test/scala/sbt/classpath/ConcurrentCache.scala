package sbt
package classpath

import org.scalacheck._
import Prop._
import java.io.File

object ConcurrentCache extends Properties("ClassLoaderCache concurrent access") {
  implicit lazy val concurrentArb: Arbitrary[Int] = Arbitrary(Gen.choose(1, 1000))
  implicit lazy val filenameArb: Arbitrary[String] = Arbitrary(Gen.alphaStr)

  private[this] object DifferentClassloader {
    def unapply(loaders: Seq[ClassLoader]): Option[(ClassLoader, ClassLoader)] =
      if (loaders.size > 1) loaders.sliding(2).find { case Seq(x, y) => x != y } map { case Seq(x, y) => (x, y) }
      else None
  }

  private def showCp(cp: ClassLoader): String = cp match {
    case u: java.net.URLClassLoader => u.getURLs.mkString("UrlClassLoader(", ", ", ")")
    case _                          => cp.toString
  }

  property("Same class loader for same classpaths concurrently processed") = forAll { (names: List[String], concurrent: Int) =>
    withcp(names.distinct) { files =>
      val cache = new ClassLoaderCache(null, errorEvicted = true)
      val loaders = (1 to concurrent).par.map(_ => cache(files)).toList
      loaders match {
        case DifferentClassloader(left, right) => false :| s"${showCp(left)} != ${showCp(right)}"
        case _                                 => true :| ""
      }
    }
  }

  private[this] def withcp[T](names: List[String])(f: List[File] => T): T = IO.withTemporaryDirectory { tmp =>
    val files = names.sorted.map { name =>
      val file = new File(tmp, name)
      IO.touch(file)
      file
    }
    f(files)
  }
}