package coursier.cli.util

import java.io.{File, FileInputStream, OutputStream}
import java.util.jar.{Attributes, JarOutputStream, Manifest}
import java.util.regex.Pattern
import java.util.zip.{ZipEntry, ZipInputStream, ZipOutputStream}

import scala.collection.mutable

object Assembly {

  sealed abstract class Rule extends Product with Serializable

  object Rule {
    sealed abstract class PathRule extends Rule {
      def path: String
    }

    final case class Exclude(path: String) extends PathRule
    final case class ExcludePattern(path: Pattern) extends Rule

    object ExcludePattern {
      def apply(s: String): ExcludePattern =
        ExcludePattern(Pattern.compile(s))
    }

    // TODO Accept a separator: Array[Byte] argument in these
    // (to separate content with a line return in particular)
    final case class Append(path: String) extends PathRule
    final case class AppendPattern(path: Pattern) extends Rule

    object AppendPattern {
      def apply(s: String): AppendPattern =
        AppendPattern(Pattern.compile(s))
    }
  }

  def make(jars: Seq[File], output: OutputStream, attributes: Seq[(Attributes.Name, String)], rules: Seq[Rule]): Unit = {

    val rulesMap = rules.collect { case r: Rule.PathRule => r.path -> r }.toMap
    val excludePatterns = rules.collect { case Rule.ExcludePattern(p) => p }
    val appendPatterns = rules.collect { case Rule.AppendPattern(p) => p }

    val manifest = new Manifest
    manifest.getMainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
    for ((k, v) <- attributes)
      manifest.getMainAttributes.put(k, v)

    var zos: ZipOutputStream = null

    try {
      zos = new JarOutputStream(output, manifest)

      val concatenedEntries = new mutable.HashMap[String, ::[(ZipEntry, Array[Byte])]]

      var ignore = Set.empty[String]

      for (jar <- jars) {
        var fis: FileInputStream = null
        var zis: ZipInputStream = null

        try {
          fis = new FileInputStream(jar)
          zis = new ZipInputStream(fis)

          for ((ent, content) <- Zip.zipEntries(zis)) {

            def append() =
              concatenedEntries += ent.getName -> ::((ent, content), concatenedEntries.getOrElse(ent.getName, Nil))

            rulesMap.get(ent.getName) match {
              case Some(Rule.Exclude(_)) =>
              // ignored

              case Some(Rule.Append(_)) =>
                append()

              case None =>
                if (!excludePatterns.exists(_.matcher(ent.getName).matches())) {
                  if (appendPatterns.exists(_.matcher(ent.getName).matches()))
                    append()
                  else if (!ignore(ent.getName)) {
                    ent.setCompressedSize(-1L)
                    zos.putNextEntry(ent)
                    zos.write(content)
                    zos.closeEntry()

                    ignore += ent.getName
                  }
                }
            }
          }

        } finally {
          if (zis != null)
            zis.close()
          if (fis != null)
            fis.close()
        }
      }

      for ((_, entries) <- concatenedEntries) {
        val (ent, _) = entries.head

        ent.setCompressedSize(-1L)

        if (entries.tail.nonEmpty)
          ent.setSize(entries.map(_._2.length).sum)

        zos.putNextEntry(ent)
        // for ((_, b) <- entries.reverse)
        //  zos.write(b)
        zos.write(entries.reverse.toArray.flatMap(_._2))
        zos.closeEntry()
      }
    } finally {
      if (zos != null)
        zos.close()
    }
  }

}
