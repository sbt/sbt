package sbt

import sbt.serialization._
import java.{ util => ju }

private[sbt] object DatePicklers {
  private val longTag = implicitly[FastTypeTag[Long]]
  implicit val datePickler: Pickler[ju.Date] = new Pickler[ju.Date] {
    val tag = implicitly[FastTypeTag[ju.Date]]
    def pickle(a: ju.Date, builder: PBuilder): Unit = {
      builder.pushHints()
      builder.hintTag(tag)
      builder.beginEntry(a)
      builder.putField("value", { b =>
        b.hintTag(longTag)
        longPickler.pickle(a.getTime, b)
      })
      builder.endEntry()
      builder.popHints()
    }
  }
  implicit val dateUnpickler: Unpickler[ju.Date] = new Unpickler[ju.Date] {
    val tag = implicitly[FastTypeTag[ju.Date]]
    def unpickle(tpe: String, reader: PReader): Any = {
      reader.pushHints()
      reader.hintTag(tag)
      reader.beginEntry()
      val a0 = longPickler.unpickleEntry(reader.readField("value")).asInstanceOf[Long]
      val result = new ju.Date(a0)
      reader.endEntry()
      reader.popHints()
      result
    }
  }
}
