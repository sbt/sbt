/*
 * sbt
 * Copyright 2023, Scala center
 * Copyright 2011 - 2022, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt
package internal

import Def._
import Keys.{ sbtVersion, state, terminal }

import java.io.{ File, FileInputStream, FileOutputStream, InputStream, IOException }
import java.net.URI
import java.nio.file.{ Files, Path }
import java.util.zip.ZipInputStream
import sbt.io.IO
import sbt.io.Path.userHome
import sbt.io.syntax._
import scala.util.{ Properties, Try }

private[sbt] object InstallSbtn {
  private[sbt] val installSbtn =
    Def.inputKey[Unit]("install sbtn and tab completions").withRank(KeyRanks.BTask)
  private[sbt] def installSbtnImpl: Def.Initialize[InputTask[Unit]] = Def.inputTask {
    val inputVersion = Def.spaceDelimited("version").parsed.headOption
    val version = inputVersion.getOrElse(sbtVersion.value.replace("-SNAPSHOT", ""))
    val term = terminal.value
    term.setMode(canonical = false, echo = false)
    val baseDirectory = BuildPaths.getGlobalBase(state.value).toPath
    val tmp = Files.createTempFile(s"sbt-$version", "zip")
    val sbtn = if (Properties.isWin) "sbtn.exe" else "sbtn"
    try extractSbtn(term, version, tmp, baseDirectory.resolve("bin").resolve(sbtn))
    finally {
      Files.deleteIfExists(tmp)
      ()
    }
    val shell = if (System.console != null) getShell(term) else "none"
    shell match {
      case "none" =>
      case s =>
        val completion = shellCompletions(s)
        val completionLocation = baseDirectory.resolve("completions").resolve(completion)
        downloadCompletion(completion, version, completionLocation)
        s match {
          case "bash"       => setupBash(baseDirectory, term)
          case "fish"       => setupFish(baseDirectory, term)
          case "zsh"        => setupZsh(baseDirectory, term)
          case "powershell" => setupPowershell(baseDirectory, term)
          case _            => // should be unreachable
        }
        val msg = s"Successfully installed sbtn for $s. You may need to restart $s for the " +
          "changes to take effect."
        term.printStream.println(msg)
    }
    ()
  }

  private[sbt] def extractSbtn(term: Terminal, version: String, sbtZip: Path, sbtn: Path): Unit = {
    downloadRelease(term, version, sbtZip)
    Files.createDirectories(sbtn.getParent)
    val bin =
      if (Properties.isWin) "pc-win32.exe"
      else if (Properties.isLinux) "pc-linux"
      else "apple-darwin"
    val isArmArchitecture: Boolean = {
      val prop = sys.props
        .getOrElse("os.arch", "")
        .toLowerCase(java.util.Locale.ROOT)
      prop == "arm64" || prop == "aarch64"
    }
    val arch =
      if (Properties.isWin) "x86_64"
      else if (Properties.isLinux && isArmArchitecture) "aarch64"
      else "universal"
    val sbtnName = s"sbt/bin/sbtn-$arch-$bin"
    val fis = new FileInputStream(sbtZip.toFile)
    val zipInputStream = new ZipInputStream(fis)
    var foundBinary = false
    try {
      var entry = zipInputStream.getNextEntry
      while (entry != null) {
        if (entry.getName == sbtnName) {
          foundBinary = true
          term.printStream.println(s"extracting $sbtZip!$sbtnName to $sbtn")
          transfer(zipInputStream, sbtn)
          sbtn.toFile.setExecutable(true)
          entry = null
        } else {
          entry = zipInputStream.getNextEntry
        }
      }
      if (!foundBinary) throw new IllegalStateException(s"couldn't find $sbtnName in $sbtZip")
    } finally {
      fis.close()
      zipInputStream.close()
    }
    ()
  }
  private[this] def downloadRelease(term: Terminal, version: String, location: Path): Unit = {
    val zip = s"https://github.com/sbt/sbt/releases/download/v$version/sbt-$version.zip"
    val url = new URI(zip).toURL
    term.printStream.println(s"downloading $zip to $location")
    transfer(url.openStream(), location)
  }
  private[this] def transfer(inputStream: InputStream, path: Path): Unit =
    try {
      val os = new FileOutputStream(path.toFile)
      try {
        val result = new Array[Byte](1024 * 1024)
        var bytesRead = -1
        do {
          bytesRead = inputStream.read(result)
          if (bytesRead > 0) os.write(result, 0, bytesRead)
        } while (bytesRead > 0)
      } finally os.close()
    } finally inputStream.close()
  private[this] def getShell(term: Terminal): String = {
    term.printStream.print(s"""Setup sbtn for shell:
      | [1] bash
      | [2] fish
      | [3] powershell
      | [4] zsh
      | [5] none
      |Enter option: """.stripMargin)
    term.printStream.flush()
    val key = term.inputStream.read
    term.printStream.println(key.toChar)
    key match {
      case 49 => "bash"
      case 50 => "fish"
      case 51 => "powershell"
      case 52 => "zsh"
      case _  => "none"
    }
  }
  private[this] def downloadCompletion(completion: String, version: String, target: Path): Unit = {
    Files.createDirectories(target.getParent)
    val comp = s"https://raw.githubusercontent.com/sbt/sbt/v$version/client/completions/$completion"
    transfer(new URI(comp).toURL.openStream, target)
  }
  private[this] def setupShell(
      shell: String,
      baseDirectory: Path,
      term: Terminal,
      configFile: File,
      setPath: Path => String,
      setCompletions: Path => String,
  ): Unit = {
    val bin = baseDirectory.resolve("bin")
    val export = setPath(bin)
    val completions = baseDirectory.resolve("completions")
    val sourceCompletions = setCompletions(completions)
    val contents = try IO.read(configFile)
    catch { case _: IOException => "" }
    if (!contents.contains(export)) {
      term.printStream.print(s"Add $bin to PATH in $configFile? y/n (y default): ")
      term.printStream.flush()
      term.inputStream.read() match {
        case 110 => term.printStream.println()
        case c =>
          term.printStream.println(c.toChar)
          // put the export at the bottom so that the ~/.sbt/1.0/bin/sbtn is least preferred
          // but still on the path
          IO.write(configFile, s"$contents\n$export")
      }
    }
    val newContents = try IO.read(configFile)
    catch { case _: IOException => "" }
    if (!newContents.contains(sourceCompletions)) {
      term.printStream.print(s"Add tab completions to $configFile? y/n (y default): ")
      term.printStream.flush()
      term.inputStream.read() match {
        case 110 =>
        case c =>
          term.printStream.println(c.toChar)
          if (shell == "zsh") {
            // delete the .zcompdump file because it can prevent the new completions from
            // being recognized
            Files.deleteIfExists((userHome / ".zcompdump").toPath)
            // put the completions at the top because it is effectively just a source
            // so the order in the file doesn't really matter but we want to make sure
            // that we set fpath before any autoload command in zsh
            IO.write(configFile, s"$sourceCompletions\n$newContents")
          } else {
            IO.write(configFile, s"$newContents\n$sourceCompletions")
          }
      }
      term.printStream.println()
    }
  }
  private[this] def setupBash(baseDirectory: Path, term: Terminal): Unit =
    setupShell(
      "bash",
      baseDirectory,
      term,
      userHome / ".bashrc",
      bin => s"export PATH=$$PATH:$bin",
      completions => s"source $completions/sbtn.bash"
    )
  private[this] def setupZsh(baseDirectory: Path, term: Terminal): Unit = {
    val comp = (completions: Path) => {
      "# The following two lines were added by the sbt installSbtn task:\n" +
        s"fpath=($$fpath $completions)\nautoload -Uz compinit; compinit"
    }
    setupShell("zsh", baseDirectory, term, userHome / ".zshrc", bin => s"path=($$path $bin)", comp)
  }
  private[this] def setupFish(baseDirectory: Path, term: Terminal): Unit = {
    val comp = (completions: Path) => s"source $completions/sbtn.fish"
    val path = (bin: Path) => s"set PATH $$PATH $bin"
    val config = userHome / ".config" / "fish" / "config.fish"
    setupShell("fish", baseDirectory, term, config, path, comp)
  }
  private[this] def setupPowershell(baseDirectory: Path, term: Terminal): Unit = {
    val comp = (completions: Path) => s""". "$completions\\sbtn.ps1""""
    val path = (bin: Path) => s"""$$env:Path += ";$bin""""
    import scala.sys.process._
    Try(Seq("pwsh", "-Command", "echo $PROFILE").!!).foreach { output =>
      output.linesIterator.toSeq.headOption.foreach { l =>
        setupShell("pwsh", baseDirectory, term, new File(l), path, comp)
      }
    }
    Try(Seq("powershell", "-Command", "echo $PROFILE").!!).foreach { output =>
      output.linesIterator.toSeq.headOption.foreach { l =>
        setupShell("pwsh", baseDirectory, term, new File(l), path, comp)
      }
    }
  }
  private[this] val shellCompletions = Map(
    "bash" -> "sbtn.bash",
    "fish" -> "sbtn.fish",
    "powershell" -> "sbtn.ps1",
    "zsh" -> "_sbtn",
  )
}
