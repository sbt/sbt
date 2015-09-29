/* sbt -- Simple Build Tool
 * Copyright 2010 Mark Harrah */

package sbt

import org.specs2._
import mutable.Specification

import IO._
import java.io.File
import Function.tupled

object CheckStash extends Specification {
  "stash" should {
    "handle empty files" in {
      stash(Set()) {}
      true must beTrue
    }

    "move files during execution" in {
      WithFiles(TestFiles: _*)(checkMove)
    }

    "restore files on exceptions but not errors" in {
      WithFiles(TestFiles: _*)(checkRestore)
    }
  }

  def checkRestore(seq: Seq[File]) = {
    allCorrect(seq)

    stash0(seq, throw new TestRuntimeException) must beFalse
    allCorrect(seq)

    stash0(seq, throw new TestException) must beFalse
    allCorrect(seq)

    stash0(seq, throw new TestError) must beFalse
    noneExist(seq)
  }
  def checkMove(seq: Seq[File]) = {
    allCorrect(seq)
    stash0(seq, ()) must beTrue
    noneExist(seq)
  }
  def stash0(seq: Seq[File], post: => Unit): Boolean =
    try {
      stash(Set() ++ seq) {
        noneExist(seq)
        post
      }
      true
    } catch {
      case _: TestError | _: TestException | _: TestRuntimeException => false
    }

  def allCorrect(s: Seq[File]) = (s.toList zip TestFiles.toList).foreach((correct _).tupled)
  def correct(check: File, ref: (File, String)) =
    {
      check.exists must beTrue
      read(check) must equalTo(ref._2)
    }
  def noneExist(s: Seq[File]) = s.forall(!_.exists) must beTrue

  lazy val TestFiles =
    Seq(
      "a/b/c" -> "content1",
      "a/b/e" -> "content1",
      "c" -> "",
      "e/g" -> "asdf",
      "a/g/c" -> "other"
    ) map {
        case (f, c) => (new File(f), c)
      }
}
class TestError extends Error
class TestRuntimeException extends RuntimeException
class TestException extends Exception
