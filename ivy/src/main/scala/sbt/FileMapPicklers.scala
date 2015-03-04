package sbt

import java.io.File
import sbt.serialization._
import java.net.URI

object FileMapPicklers {
  implicit def fileMapPickler[A: Pickler: Unpickler: FastTypeTag]: Pickler[Map[File, A]] with Unpickler[Map[File, A]] = new Pickler[Map[File, A]] with Unpickler[Map[File, A]] {
    val tag = implicitly[FastTypeTag[Map[File, A]]]
    val stringAMapPickler = implicitly[Pickler[Map[String, A]]]
    val stringAMapUnpickler = implicitly[Unpickler[Map[String, A]]]

    def pickle(m: Map[File, A], builder: PBuilder): Unit =
      stringAMapPickler.pickle(Map(m.toSeq map { case (k, v) => (k.toURI.toASCIIString, v) }: _*), builder)

    def unpickle(tpe: String, reader: PReader): Any =
      Map(stringAMapUnpickler.unpickle(tpe, reader).asInstanceOf[Map[String, A]].toSeq map {
        case (k, v) =>
          (new File(new URI(k)), v)
      }: _*).asInstanceOf[Map[File, A]]
  }
}
