package sbt
package inc

import java.lang.reflect.InvocationTargetException
import javax.xml.bind.DatatypeConverter

private[sbt] trait Base64 {
  def encode(bytes: Array[Byte]): String

  def decode(string: String): Array[Byte]
}

private[sbt] object Base64 {
  lazy val factory: () => Base64 = {
    try {
      new Java678Encoder().encode(Array[Byte]())
      () => new Java678Encoder()
    } catch {
      case _: LinkageError =>
        () => new Java89Encoder
    }
  }
}

private[sbt] class Java678Encoder extends Base64 {
  def encode(bytes: Array[Byte]): String = DatatypeConverter.printBase64Binary(bytes)

  def decode(string: String): Array[Byte] = DatatypeConverter.parseBase64Binary(string)
}

private[sbt] object Java89Encoder {

  import scala.runtime.ScalaRunTime.ensureAccessible

  private val Base64_class = Class.forName("java.util.Base64")
  private val Base64_getEncoder = ensureAccessible(Base64_class.getMethod("getEncoder"))
  private val Base64_getDecoder = ensureAccessible(Base64_class.getMethod("getDecoder"))
  private val Base64_Encoder_class = Class.forName("java.util.Base64$Encoder")
  private val Base64_Decoder_class = Class.forName("java.util.Base64$Decoder")
  private val Base64_Encoder_encodeToString = ensureAccessible(Base64_Encoder_class.getMethod("encodeToString", classOf[Array[Byte]]))
  private val Base64_Decoder_decode = ensureAccessible(Base64_Decoder_class.getMethod("decode", classOf[String]))
}

private[sbt] class Java89Encoder extends Base64 {

  import Java89Encoder._

  def encode(bytes: Array[Byte]): String = try {
    val encoder = Base64_getEncoder.invoke(null)
    Base64_Encoder_encodeToString.invoke(encoder, bytes).asInstanceOf[String]
  } catch {
    case ex: InvocationTargetException => throw ex.getCause
  }

  def decode(string: String): Array[Byte] = try {
    val decoder = Base64_getDecoder.invoke(null)
    Base64_Decoder_decode.invoke(decoder, string).asInstanceOf[Array[Byte]]
  } catch {
    case ex: InvocationTargetException => throw ex.getCause
  }
}
