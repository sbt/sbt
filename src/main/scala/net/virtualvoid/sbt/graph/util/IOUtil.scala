/*
 * Copyright 2015 Johannes Rudolph
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package net.virtualvoid.sbt.graph.util

import java.io.{ OutputStream, InputStream, FileOutputStream, File }
import java.nio.charset.Charset

import scala.annotation.tailrec

object IOUtil {
  val utf8 = Charset.forName("utf8")

  def writeToFile(string: String, file: File): Unit =
    sbt.IO.write(file, string, utf8)

  def saveResource(resourcePath: String, to: File): Unit = {
    val is = getClass.getClassLoader.getResourceAsStream(resourcePath)
    require(is ne null, s"Couldn't load '$resourcePath' from classpath.")

    val fos = new FileOutputStream(to)
    try copy(is, fos)
    finally {
      is.close()
      fos.close()
    }
  }

  def copy(from: InputStream, to: OutputStream): Unit = {
    val buffer = new Array[Byte](65536)

    @tailrec def rec(): Unit = {
      val read = from.read(buffer)
      if (read > 0) {
        to.write(buffer, 0, read)
        rec()
      } else if (read == 0)
        throw new IllegalStateException("InputStream.read returned 0")
    }
    rec()
  }
}
