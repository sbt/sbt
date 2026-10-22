package sbt

import java.nio.file.Paths
import java.io.FileInputStream
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.MethodVisitor
import org.objectweb.asm.Opcodes.ASM9
import scala.util.Using
import verify.BasicTestSuite

object SunMiscUnsafeLazyValSpec extends BasicTestSuite:
  test("no old lazy val") {
    val exclude = Seq(
      "com.eed3si9n.verify",
      "org.scalacheck",
      "org.scalactic",
      "org.scalatest",
    ).map(x =>
      Seq(
        java.io.File.separator,
        x.replace('.', java.io.File.separatorChar),
        java.io.File.separator,
      ).mkString
    )
    val list = this.getClass.getClassLoader
      .asInstanceOf[java.net.URLClassLoader]
      .getURLs
      .toList
      .map(url => Paths.get(url.toURI).toFile)
      .filterNot(f => exclude.exists(f.getAbsolutePath.contains))
    assert(list.size == 82)
    val result = list.map(jarOrDir => jarOrDir -> oldLazyVal(jarOrDir)).filter(_._2.nonEmpty)
    assert(result.isEmpty)
  }

  // https://github.com/scala/scala3/blob/72c3a6629529031ac/library/src/scala/runtime/LazyVals.scala
  private val unsafeMethodNames: Set[String] = Set(
    "CAS",
    "get",
    "getOffset",
    "getOffsetStatic",
    "getStaticFieldOffset",
    "objCAS",
    "setFlag",
    "wait4Notification",
  )

  private def oldLazyVal(jarFileOrDir: File): List[(String, Int)] =
    IO.withTemporaryDirectory { tempDir =>
      val dir =
        if jarFileOrDir.isFile then
          IO.unzip(jarFileOrDir, tempDir)
          tempDir
        else jarFileOrDir

      val classFiles = (dir ** "*.class")
        .get()
        .toList

      assert(classFiles.nonEmpty)

      classFiles
        .flatMap { classFile =>
          var counter: Int = 0
          Using.resource(new FileInputStream(classFile)) { in =>
            val reader = new ClassReader(in)
            val myVisitor = new ClassVisitor(ASM9):
              override def visitMethod(
                  access: Int,
                  name: String,
                  descriptor: String,
                  signature: String,
                  exceptions: Array[String]
              ): MethodVisitor =
                new MethodVisitor(ASM9):
                  override def visitMethodInsn(
                      opcode: Int,
                      owner: String,
                      name: String,
                      descriptor: String,
                      isInterface: Boolean
                  ): Unit =
                    owner match
                      case "scala/runtime/LazyVals$" if unsafeMethodNames(name) =>
                        counter += 1
                      case _ =>
            reader.accept(myVisitor, 0)
            if counter > 0 then Some(IO.relativize(dir, classFile).get -> counter)
            else None
          }
        }
        .distinct
        .sorted
    }
end SunMiscUnsafeLazyValSpec
