package sbt

import sbt.serialization._

private[sbt] object PairPicklers {
  implicit def pairPickler[A1: FastTypeTag: Pickler: Unpickler, A2: FastTypeTag: Pickler: Unpickler](): Pickler[(A1, A2)] with Unpickler[(A1, A2)] =
    new Pickler[(A1, A2)] with Unpickler[(A1, A2)] {
      val a1Tag = implicitly[FastTypeTag[A1]]
      val a2Tag = implicitly[FastTypeTag[A2]]
      val tag = implicitly[FastTypeTag[(A1, A2)]]
      val a1Pickler = implicitly[Pickler[A1]]
      val a1Unpickler = implicitly[Unpickler[A1]]
      val a2Pickler = implicitly[Pickler[A2]]
      val a2Unpickler = implicitly[Unpickler[A2]]

      def pickle(a: (A1, A2), builder: PBuilder): Unit = {
        builder.pushHints()
        builder.hintTag(tag)
        builder.beginEntry(a)
        builder.putField("_1", { b =>
          b.hintTag(a1Tag)
          a1Pickler.pickle(a._1, b)
        })
        builder.putField("_2", { b =>
          b.hintTag(a2Tag)
          a2Pickler.pickle(a._2, b)
        })
        builder.endEntry()
        builder.popHints()
      }

      def unpickle(tpe: String, reader: PReader): Any = {
        reader.pushHints()
        reader.hintTag(tag)
        reader.beginEntry()
        val a0 = a1Unpickler.unpickleEntry(reader.readField("_1")).asInstanceOf[A1]
        val a1 = a2Unpickler.unpickleEntry(reader.readField("_2")).asInstanceOf[A2]
        val result = (a0, a1)
        reader.endEntry()
        reader.popHints()
        result
      }
    }
}
