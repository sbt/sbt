/* sbt -- Simple Build Tool
 * Copyright 2009 Mark Harrah
 */
package sbt

import java.io.{File, FileOutputStream}
import java.util.jar.{JarEntry, JarFile, JarOutputStream, Pack200}
import IO._

object Pack
{
	def pack(jarPath: File, out: File): Unit = pack(jarPath, out, defaultPackerOptions)
	def pack(jarPath: File, out: File, options: Iterable[(String, String)])
	{
		val packer = Pack200.newPacker
		import collection.JavaConversions._
		packer.properties ++= options
		 
		Using.jarFile(false)(jarPath) { f =>
			Using.fileOutputStream()(out) { stream =>
				packer.pack(f, stream)
			}
		}
	}
	def unpack(packedPath: File, toJarPath: File)
	{
		val unpacker = Pack200.newUnpacker
		Using.fileOutputStream()(toJarPath) { fileStream =>
			Using.jarOutputStream(fileStream) { jarOut =>
				unpacker.unpack(packedPath, jarOut)
			}
		}
	}
	def defaultPackerOptions = scala.collection.immutable.Map()
}

import java.net.URL
/** This is somewhat of a mess and is not entirely correct.  jarsigner doesn't work properly
* on scalaz and it is difficult to determine whether a jar is both signed and valid.  */
object SignJar
{
	final class SignOption private[SignJar](val toList: List[String], val signOnly: Boolean)
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
	def sign(jarPath: File, alias: String, options: Seq[SignOption])(fork: (String, List[String]) => Int)
	{
		require(!alias.trim.isEmpty, "Alias cannot be empty")
		val arguments = options.toList.flatMap(_.toList) ::: jarPath.getAbsolutePath :: alias :: Nil
		execute("signing", arguments)(fork)
	}
	/** Uses jarsigner to verify the given jar.*/
	def verify(jarPath: File, options: Seq[SignOption])(fork: (String, List[String]) => Int)
	{
		val arguments = options.filter(!_.signOnly).toList.flatMap(_.toList) ::: VerifyOption :: jarPath.getAbsolutePath :: Nil
		execute("verifying", arguments)(fork)
	}
	private def execute(action: String, arguments: List[String])(fork: (String, List[String]) => Int)
	{
		val exitCode = fork(CommandName, arguments)
		if(exitCode != 0)
			error("Error " + action + " jar (exit code was " + exitCode + ".)")
	}
	
	private val CommandName = "jarsigner"
}