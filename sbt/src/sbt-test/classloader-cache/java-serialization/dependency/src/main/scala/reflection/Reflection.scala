package reflection

import java.io._
import scala.util.control.NonFatal

object Reflection {
  def roundTrip[A](a: A): A = {
    val baos = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(baos)
    oos.writeObject(a)
    oos.close()
    val bais = new ByteArrayInputStream(baos.toByteArray())
    val ois = new ObjectInputStream(bais)
    try ois.readObject().asInstanceOf[A]
    finally ois.close()
  }
}
