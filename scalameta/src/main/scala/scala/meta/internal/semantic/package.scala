package scala.meta
package internal

import org.scalameta.unreachable
import org.scalameta.invariants._
import scala.collection.mutable
import scala.meta.internal.ast._
import scala.meta.internal.ui.Attributes

package object semantic {
  implicit class XtensionHygieneDebug(debug: org.scalameta.debug.Debug.type) {
    def hygiene = sys.props("hygiene.debug") != null
  }

  implicit class XtensionAttributedTree(tree: scala.meta.Tree) {
    def maybeEnv: Option[Environment] = tree match {
      case tree: Name => Some(tree.env)
      case tree: Term.Apply => Some(tree.env)
      case tree: Term.ApplyInfix => Some(tree.env)
      case tree: Term.ApplyType => Some(tree.env)
      case tree: Term.ApplyUnary => Some(tree.env)
      case tree: Term.Assign => Some(tree.env)
      case tree: Term.Update => Some(tree.env)
      case tree: Term.Interpolate => Some(tree.env)
      case _ => None
    }

    def maybeDenot: Option[Denotation] = tree match {
      case tree: Name => Some(tree.denot)
      case _ => None
    }

    def maybeTyping: Option[Typing] = tree match {
      case tree: Term => Some(tree.typing)
      case tree: Term.Param => Some(tree.typing)
      case _ => None
    }

    def maybeExpansion: Option[Expansion] = tree match {
      case tree: Term => Some(tree.expansion)
      case _ => None
    }

    def requireAttributed(): Unit = {
      val offenders = mutable.ListBuffer[(Tree, List[String])]()
      def traverse(tree: Tree, path: List[String]): Unit = {
        def check(tree: Tree): Boolean = {
          def checkEnv(tree: Tree): Boolean = tree.maybeEnv.map(_ match {
            case Environment.Zero => true
            case _ => false
          }).getOrElse(true)

          def checkDenot(tree: Tree): Boolean = tree.maybeDenot.map(_ match {
            case Denotation.Single(Prefix.Zero, _) =>
              true
            case Denotation.Single(Prefix.Type(prefix: Tree), _) =>
              traverse(prefix, path :+ "Denotation")
              true
            case Denotation.Multi(Prefix.Zero, _) =>
              true
            case Denotation.Multi(Prefix.Type(prefix: Tree), _) =>
              traverse(prefix, path :+ "Denotation")
              true
            case _ =>
              false
          }).getOrElse(true)

          def checkTyping(tree: Tree): Boolean = tree.maybeTyping.map(_ match {
            case Typing.Recursive => true
            case Typing.Nonrecursive(_) => true
            case _ => false
          }).getOrElse(true)

          def checkExpansion(tree: Tree): Boolean = tree.maybeExpansion.map(_ match {
            case Expansion.Identity => true
            case Expansion.Desugaring(_) => true
            case _ => false
          }).getOrElse(true)

          checkEnv(tree) && checkDenot(tree) && checkTyping(tree) && checkExpansion(tree)
        }
        def loop(x: Any): Unit = x match {
          case x: Tree => traverse(x, path :+ tree.productPrefix)
          case x: List[_] => x.foreach(loop)
          case x: Some[_] => loop(x.get)
          case x => // do nothing
        }
        if (!check(tree)) offenders += ((tree, path))
        tree.productIterator.toList.map(loop)
      }
      traverse(tree.require[Tree], Nil)
      if (offenders.nonEmpty) {
        val grouped = offenders.groupBy(_._1.productPrefix).toList.sortBy(_._1)
        def commaCommaAnd[T](list: List[T]): String = list.init.mkString(", ") + (if (list.length == 1) "" else " and ") + list.last
        // The problem is caused by 6 Idents that are either unattributed or erroneous:
        val offenderSummary = commaCommaAnd(grouped.map{ case (k, v) => s"${v.length} $k${if (v.length == 1) "" else "s"}" })
        // 1: _ (... CaseDef > Bind > UnApply > Typed > Ident)
        // 2: _ (...  DefDef > Match > CaseDef > Bind > Ident)
        // ...
        val offenderPrintout = grouped.flatMap(_._2).map({ case (tree, path) =>
          var prefix = tree.toString.replace("\n", " ")
          if (prefix.length > 60) prefix = prefix.take(60) + "..."
          val s_path = path.mkString(" > ")
          var suffix = if (s_path.length <= 40) s_path else "... " + s_path.takeRight(40)
          if (suffix != "") suffix = s" ($suffix)"
          s"$prefix$suffix"
        }).zipWithIndex.map{ case (s, i) => s"${i + 1}: $s" }.mkString("\n")
        sys.error(s"""
          |scala.meta tree is not fully attributed,
          |because $offenderSummary ${if (offenders.length == 1) "doesn't" else "don't"} have denotations:
          |$offenderPrintout
          |The tree that has caused problems is printed out below in its entirety:
          |$tree
          |${tree.show[Attributes]}
        """.stripMargin)
      }
    }
  }
}