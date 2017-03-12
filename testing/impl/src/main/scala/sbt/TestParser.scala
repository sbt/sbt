/* sbt -- Simple Build Tool
 * Copyright 2008, 2009 Mark Harrah
 */
package sbt

import testing.{ AnnotatedFingerprint, Fingerprint, TestFingerprint }

sealed abstract class Discovered extends Fingerprint with NotNull {

  /** Whether a test is a module or a class*/
  def isModule: Boolean
  def className: String
  // for TestFingerprint
  def testClassName = className
  def toDefinition: TestDefinition = new TestDefinition(className, this)
}

/** Represents a class 'className' that has 'superClassName' as an ancestor.*/
final case class DiscoveredSubclass(isModule: Boolean, className: String, superClassName: String)
    extends Discovered
    with TestFingerprint {
  override def toString =
    (if (isModule) IsModuleLiteral else "") + className + SubSuperSeparator + superClassName
}

/** Represents an annotation on a method or class.*/
final case class DiscoveredAnnotated(isModule: Boolean, className: String, annotationName: String)
    extends Discovered
    with AnnotatedFingerprint {
  override def toString =
    (if (isModule) IsModuleLiteral else "") + className + AnnotationSeparator + annotationName
}
