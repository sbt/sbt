package xsbt.boot

import org.scalacheck._
import Prop._
import Configuration._
import java.io.File
import java.net.URI

object URITests extends Properties("URI Tests")
{
	val FileProtocol = "file"
	property("directoryURI adds trailing slash") = secure {
		val dirURI = directoryURI(new File("/a/b/c"))
		val directURI = filePathURI("/a/b/c/")
		dirURI == directURI
	}
	property("directoryURI preserves trailing slash") = secure {
		directoryURI(new File("/a/b/c/")) == filePathURI("/a/b/c/")
	}

	property("filePathURI encodes spaces") = secure {
		val decoded = "has spaces"
		val encoded = "has%20spaces"
		val fpURI = filePathURI(decoded)
		val directURI = new URI(encoded)
		("filePathURI: " +fpURI) |:
		("direct URI: " + directURI) |:
		("getPath: " + fpURI.getPath) |:
		("getRawPath: " + fpURI.getRawPath) |:
		(fpURI == directURI) &&
		(fpURI.getPath == decoded) &&
		(fpURI.getRawPath == encoded)
	}

	property("filePathURI and File.toURI agree for absolute file") =  secure {
		val s = "/a/b'/has spaces"
		val viaPath = filePathURI(s)
		val viaFile = (new File(s)).toURI
		("via path: " + viaPath) |:
		("via file: " + viaFile) |:
		(viaPath == viaFile)
	}
}