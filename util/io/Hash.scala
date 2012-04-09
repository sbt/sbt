/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import java.io.{ByteArrayInputStream, File, InputStream}
import java.net.URL

object Hash
{
	private val BufferSize = 8192
	def toHex(bytes: Array[Byte]): String =
	{
		val buffer = new StringBuilder(bytes.length * 2)
		for(i <- 0 until bytes.length)
		{
			val b = bytes(i)
			val bi: Int = if(b < 0) b + 256 else b
			buffer append toHex((bi >>> 4).asInstanceOf[Byte])
			buffer append toHex((bi & 0x0F).asInstanceOf[Byte])
		}
		buffer.toString
	}
	def fromHex(hex: String): Array[Byte] =
	{
		require((hex.length & 1) == 0, "Hex string must have length 2n.")
		val array = new Array[Byte](hex.length >> 1)
		for(i <- 0 until hex.length by 2)
		{
			val c1 = hex.charAt(i)
			val c2 = hex.charAt(i+1)
			array(i >> 1) = ((fromHex(c1) << 4) | fromHex(c2)).asInstanceOf[Byte]
		}
		array
	}
	def halve(s: String): String = if(s.length > 3) s.substring(0, s.length / 2) else s
	def trimHashString(s: String, i: Int): String = toHex(apply(s)).take(i)
	def halfHashString(s: String): String = halve(toHex(apply(s)))

	/** Calculates the SHA-1 hash of the given String.*/
	def apply(s: String): Array[Byte] = apply(s.getBytes("UTF-8"))
	/** Calculates the SHA-1 hash of the given Array[Byte].*/
	def apply(as: Array[Byte]): Array[Byte] = apply(new ByteArrayInputStream(as))
	/** Calculates the SHA-1 hash of the given file.*/
	def apply(file: File): Array[Byte] = Using.fileInputStream(file)(apply)
	/** Calculates the SHA-1 hash of the given resource.*/
	def apply(url: URL): Array[Byte] = Using.urlInputStream(url)(apply)
	/** Calculates the SHA-1 hash of the given stream, closing it when finished.*/
	def apply(stream: InputStream): Array[Byte] =
	{
		import java.security.{MessageDigest, DigestInputStream}
		val digest = MessageDigest.getInstance("SHA")
		try
		{
			val dis = new DigestInputStream(stream, digest)
			val buffer = new Array[Byte](BufferSize)
			while(dis.read(buffer) >= 0) {}
			dis.close()
			digest.digest
		}
		finally { stream.close() }
	}

	private def toHex(b: Byte): Char =
	{
		require(b >= 0 && b <= 15, "Byte " + b + " was not between 0 and 15")
		if(b < 10)
			('0'.asInstanceOf[Int] + b).asInstanceOf[Char]
		else
			('a'.asInstanceOf[Int] + (b-10)).asInstanceOf[Char]
	}
	private def fromHex(c: Char): Int =
	{
		val b =
			if(c >= '0' && c <= '9')
				(c - '0')
			else if(c >= 'a' && c <= 'f')
				(c - 'a') + 10
			else if(c >= 'A' && c <= 'F')
				(c - 'A') + 10
			else
				throw new RuntimeException("Invalid hex character: '" + c + "'.")
		b
	}
}