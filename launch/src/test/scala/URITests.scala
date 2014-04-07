package xsbt.boot

import org.scalacheck._
import Prop._
import Configuration._
import java.io.File
import java.net.URI

object URITests extends Properties("URI Tests")
{
	// Need a platform-specific root, otherwise URI will not be absolute (e.g. if we use a "/a/b/c" path in Windows)
	// Note:
	// If I use "C:" instead of "/C:", then isAbsolute == true for the resulting URI, but resolve is broken:
	// e.g. scala> new URI("file", "c:/a/b'/has spaces", null).resolve("a")                  broken
	//      res0: java.net.URI = a
	//      scala> new URI("file", "/c:/a/b'/has spaces", null).resolve("a")                 working
	//      res1: java.net.URI = file:/c:/a/b'/a
	val Root = if (xsbt.boot.Pre.isWindows) "/C:/" else "/"

	val FileProtocol = "file"
	property("directoryURI adds trailing slash") = secure {
		val dirURI = directoryURI(new File(Root + "a/b/c"))
		val directURI = filePathURI(Root + "a/b/c/")
		dirURI == directURI
	}
	property("directoryURI preserves trailing slash") = secure {
		directoryURI(new File(Root + "a/b/c/")) == filePathURI(Root + "a/b/c/")
	}

	property("filePathURI encodes spaces") = secure {
		val decoded = "has spaces"
		val encoded = "has%20spaces"
		val fpURI = filePathURI(decoded)
		val directURI = new URI(encoded)
		s"filePathURI: $fpURI" |:
		s"direct URI: $directURI" |:
		s"getPath: ${fpURI.getPath}" |:
		s"getRawPath: ${fpURI.getRawPath}" |:
		(fpURI == directURI) &&
		(fpURI.getPath == decoded) &&
		(fpURI.getRawPath == encoded)
	}

	property("filePathURI and File.toURI agree for absolute file") =  secure {
		val s = Root + "a/b'/has spaces"
		val viaPath = filePathURI(s)
		val viaFile = new File(s).toURI
		s"via path: $viaPath" |:
		s"via file: $viaFile" |:
		(viaPath == viaFile)
	}

	property("filePathURI supports URIs") =  secure {
		val s = s"file://${Root}is/a/uri/with%20spaces"
		val decoded = Root + "is/a/uri/with spaces"
		val encoded = Root + "is/a/uri/with%20spaces"
		val fpURI = filePathURI(s)
		val directURI = new URI(s)
		s"filePathURI: $fpURI" |:
		s"direct URI: $directURI" |:
		s"getPath: ${fpURI.getPath}" |:
		s"getRawPath: ${fpURI.getRawPath}" |:
		(fpURI == directURI) &&
		(fpURI.getPath == decoded) &&
		(fpURI.getRawPath == encoded)
	}

}