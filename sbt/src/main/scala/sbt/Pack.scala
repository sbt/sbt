/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import java.io.{File, FileOutputStream}
import java.util.jar.{JarEntry, JarFile, JarOutputStream, Pack200}
import scala.collection.Map
import FileUtilities._

object Pack
{
	def pack(jarPath: Path, out: Path, log: Logger): Option[String] = pack(jarPath, out, defaultPackerOptions, log)
	def pack(jarPath: Path, out: Path, options: Map[String, String], log: Logger): Option[String] =
	{
		val packer = Pack200.newPacker
		val properties = new wrap.MutableMapWrapper(packer.properties)
		properties ++= options
		 
		OpenResource.jarFile(false).ioOption(jarPath.asFile, "applying pack200 compression to jar", log) { f =>
			writeStream(out.asFile, log) { stream =>
				packer.pack(f, stream)
				None
			}
		}
	}
	def unpack(packedPath: Path, toJarPath: Path, log: Logger): Option[String] =
	{
		val unpacker = Pack200.newUnpacker
		writeStream(toJarPath.asFile, log) { fileStream =>
			val jarOut = new JarOutputStream(fileStream)
			Control.trapUnitAndFinally("Error unpacking '" + packedPath + "': ", log)
				{ unpacker.unpack(packedPath.asFile, jarOut); None }
				{ jarOut.close() }
		}
	}
	def defaultPackerOptions: Map[String, String] = scala.collection.immutable.Map()
}

import java.net.URL
/** This is somewhat of a mess and is not entirely correct.  jarsigner doesn't work properly
* on scalaz and it is difficult to determine whether a jar is both signed and valid.  */
object SignJar
{
	final class SignOption private[SignJar](val toList: List[String], val signOnly: Boolean) extends NotNull
	{
		override def toString = toList.mkString(" ")
	}
	def keyStore(url: URL) = new SignOption("-keystore" :: url.toExternalForm :: Nil, true)
	def signedJar(p: Path) = new SignOption("-signedjar" :: p.asFile.getAbsolutePath :: Nil, true)
	def verbose = new SignOption("-verbose" :: Nil, false)
	def sigFile(name: String) = new SignOption("-sigfile" :: name :: Nil, true)
	def storeType(t: String) = new SignOption("-storetype" :: t :: Nil, false)
	def provider(p: String) = new SignOption("-provider" :: p :: Nil, false)
	def providerName(p: String) = new SignOption("-providerName" :: p :: Nil, false)
	def storePassword(p: String) = new SignOption("-storepass" :: p :: Nil, true)
	def keyPassword(p: String) = new SignOption("-keypass" :: p :: Nil, true)
	
	private def VerifyOption = "-verify"
	
	/** Uses jarsigner to sign the given jar.  */
	def sign(jarPath: Path, alias: String, options: Seq[SignOption], log: Logger): Option[String] =
	{
		require(!alias.trim.isEmpty, "Alias cannot be empty")
		val arguments = options.toList.flatMap(_.toList) ::: jarPath.asFile.getAbsolutePath :: alias :: Nil
		execute("Signed " + jarPath, "signing", arguments, log)
	}
	/** Uses jarsigner to verify the given jar.*/
	def verify(jarPath: Path, options: Seq[SignOption], log: Logger): Option[String] =
	{
		val arguments = options.filter(!_.signOnly).toList.flatMap(_.toList) ::: VerifyOption :: jarPath.asFile.getAbsolutePath :: Nil
		execute("Verified " + jarPath, "verifying", arguments, log)
	}
	private def execute(successMessage: String, action: String, arguments: List[String], log: Logger): Option[String] =
	{
		val exitCode = Process(CommandName, arguments) ! log
		if(exitCode == 0)
		{
			log.debug(successMessage)
			None
		}
		else
			Some("Error " + action + " jar (exit code was " + exitCode + ".)")
	}
	
	private val CommandName = "jarsigner"
}