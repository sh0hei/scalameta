package org.scalameta.internal

import scala.reflect.macros.blackbox.Context

trait MacroCompat {
  val c: Context
  import c.universe._
  val AssignOrNamedArg = NamedArg
  type AssignOrNamedArg = NamedArg
}

object MacroCompat {
  val productFieldNamesAvailable = true
}

object ScalaCompat {
  // Removed in 2.13
  trait IndexedSeqOptimized[+A]
  implicit class XtensionScala213ToSeq[T](seq: collection.Seq[T]) {
    def toScalaSeq: Seq[T] = seq.toSeq
  }
}
