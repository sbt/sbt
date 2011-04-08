package sbt
package compiler

	import java.io.File
	import org.specs.Specification

object CheckBasic extends Specification
{
	val basicName = new File("Basic.scala")
	val basicSource = "package org.example { /** A comment */ object Basic }"

	"Compiling basic file should succeed" in {
		WithFiles(basicName -> basicSource){ files =>
			for(scalaVersion <- TestCompile.allVersions)
			{
				TestCompile(scalaVersion, files){ loader => Class.forName("org.example.Basic", false, loader) }
				true must beTrue // don't know how to just check that previous line completes without exception
			}
		}
	}
	"Scaladoc on basic file should succeed" in {
		WithFiles(basicName -> basicSource){ files =>
			for(scalaVersion <- TestCompile.allVersions)
			{
				IO.withTemporaryDirectory { outputDirectory =>
					WithCompiler(scalaVersion) { (compiler, log) =>
						compiler.doc(files.toSeq, Nil, outputDirectory, Nil, 5, log)
					}
				}
				true must beTrue // don't know how to just check that previous line completes without exception
			}
		}
	}
	"Analyzer plugin should send source begin and end" in {
		WithFiles(basicName -> basicSource) { files =>
			for(scalaVersion <- TestCompile.allVersions)
				CallbackTest.simple(scalaVersion, files) { callback =>
					(callback.beganSources) must haveTheSameElementsAs(files)
					(callback.endedSources) must haveTheSameElementsAs(files)
				}
		}
	}
}