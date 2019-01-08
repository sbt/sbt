/*
 * sbt
 * Copyright 2011 - 2018, Lightbend, Inc.
 * Copyright 2008 - 2010, Mark Harrah
 * Licensed under Apache License 2.0 (see LICENSE)
 */

package sbt.internal

/**
 * Provides extension methods that allow us to wrap FunctionN instances with another FunctionN that
 * delegates to the input FunctionN, but overrides the toString method with a user specified
 * label. Anonymous functions have the default java toString which is just the class name. Because
 * the anonymous class is generated implicitly, it can be impossible to tell which anonymous
 * class you are looking at if there are multiple anonymous functions of the same type in a
 * given object/class/package. We can overcome this limitation by explicitly setting the toString
 * of the anonymous function instances. This is a fairly crude solution to the problem that
 * is not ready to be exposed to users. Until the api matures or we decide that it's worth
 * exposing to users, these should remain sbt package private.
 */
private[sbt] object LabeledFunctions {

  /**
   * Adds extension methods to a zero argument function.
   * @param f the function to extend
   * @tparam R the function result type
   */
  private[sbt] implicit class Function0Ops[R](val f: () => R) extends AnyVal {

    /**
     * Add a label to the function.
     * @param string the new toString method for the function
     * @return a wrapped function with an overridden toString method.
     */
    def label(string: String): () => R = new LabeledFunction0(f, string)
  }

  /**
   * Adds extension methods to a single argument function.
   * @param f the function to extend
   * @tparam T the input parameter
   * @tparam R the function result type
   */
  private[sbt] implicit class Function1Ops[T, R](val f: T => R) extends AnyVal {

    /**
     * Add a label to the function.
     * @param string the new toString method for the function
     * @return a wrapped function with an overridden toString method.
     */
    def label(string: String): T => R = new LabeledFunction1(f, string)
  }

  /**
   * Adds extension methods to a two argument function.
   * @param f the function to extend
   * @tparam T1 the first function input parameter
   * @tparam T2 the second function input parameter
   * @tparam R the function result type
   */
  private[sbt] implicit class Function2Ops[T1, T2, R](val f: (T1, T2) => R) extends AnyVal {

    /**
     * Add a label to the function.
     * @param string the new toString method for the function
     * @return a wrapped function with an overridden toString method.
     */
    def label(string: String): (T1, T2) => R = new LabeledFunction2(f, string)
  }

  /**
   * Adds extension methods to a three argument function.
   * @param f the function to extend
   * @tparam T1 the first function input parameter
   * @tparam T2 the second function input parameter
   * @tparam T3 the third function input parameter
   * @tparam R the function result type
   */
  private[sbt] implicit class Function3Ops[T1, T2, T3, R](val f: (T1, T2, T3) => R) extends AnyVal {

    /**
     * Add a label to the function.
     * @param string the new toString method for the function
     * @return a wrapped function with an overridden toString method.
     */
    def label(string: String): (T1, T2, T3) => R = new LabeledFunction3(f, string)
  }

  /**
   * Adds extension methods to a three argument function.
   * @param f the function to extend
   * @tparam T1 the first function input parameter
   * @tparam T2 the second function input parameter
   * @tparam T3 the third function input parameter
   * @tparam T4 the fourth function input parameter
   * @tparam R the function result type
   */
  private[sbt] implicit class Function4Ops[T1, T2, T3, T4, R](val f: (T1, T2, T3, T4) => R)
      extends AnyVal {

    /**
     * Add a label to the function.
     * @param string the new toString method for the function
     * @return a wrapped function with an overridden toString method.
     */
    def label(string: String): (T1, T2, T3, T4) => R = new LabeledFunction4(f, string)
  }
  private class LabeledFunction0[+R](private val f: () => R, label: String) extends (() => R) {
    override def apply(): R = f()
    override def toString: String = label
    override def equals(o: Any): Boolean = o match {
      case that: LabeledFunction0[R] @unchecked => this.f == that.f
      case that: (() => R) @unchecked           => this.f == that
      case _                                    => false
    }
    override def hashCode: Int = f.hashCode
  }
  private class LabeledFunction1[-T, +R](private val f: T => R, label: String) extends (T => R) {
    override def apply(t: T): R = f(t)
    override def toString: String = label
    override def equals(o: Any): Boolean = o match {
      case that: LabeledFunction1[T, R] @unchecked => this.f == that.f
      case that: (T => R) @unchecked               => this.f == that
      case _                                       => false
    }
    override def hashCode: Int = f.hashCode
  }
  private class LabeledFunction2[-T1, -T2, +R](private val f: (T1, T2) => R, label: String)
      extends ((T1, T2) => R) {
    override def apply(t1: T1, t2: T2): R = f(t1, t2)
    override def toString: String = label
    override def equals(o: Any): Boolean = o match {
      case that: LabeledFunction2[T1, T2, R] @unchecked => this.f == that.f
      case that: ((T1, T2) => R) @unchecked             => this.f == that
      case _                                            => false
    }
    override def hashCode: Int = f.hashCode
  }
  private class LabeledFunction3[-T1, -T2, -T3, +R](private val f: (T1, T2, T3) => R, label: String)
      extends ((T1, T2, T3) => R) {
    override def apply(t1: T1, t2: T2, t3: T3): R = f(t1, t2, t3)
    override def toString: String = label
    override def equals(o: Any): Boolean = o match {
      case that: LabeledFunction3[T1, T2, T3, R] @unchecked => this.f == that.f
      case that: ((T1, T2, T3) => R) @unchecked             => this.f == that
      case _                                                => false
    }
    override def hashCode: Int = f.hashCode
  }
  private class LabeledFunction4[-T1, -T2, -T3, T4, +R](
      private val f: (T1, T2, T3, T4) => R,
      label: String
  ) extends ((T1, T2, T3, T4) => R) {
    override def apply(t1: T1, t2: T2, t3: T3, t4: T4): R = f(t1, t2, t3, t4)
    override def toString: String = label
    override def equals(o: Any): Boolean = o match {
      case that: LabeledFunction4[T1, T2, T3, T4, R] @unchecked => this.f == that.f
      case that: ((T1, T2, T3, T4) => R) @unchecked             => this.f == that
      case _                                                    => false
    }
    override def hashCode: Int = f.hashCode
  }
}
