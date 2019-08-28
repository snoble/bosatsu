package org.bykn.bosatsu

import cats.Eval

import cats.implicits._

import Evaluation.Value

sealed abstract class FfiCall[T[_]] {
  def call(t: rankn.Type, pn: PackageName, dn: Identifier)(
    implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]],
    valueToTag: Eval[Value[T]] => T[Value[T]]
  ): Eval[Value[T]]
}

object FfiCall {
  final case class Fn1[T[_]](fn: Value[T] => Value[T])(implicit valueT: Evaluation.ValueT[T]) extends FfiCall[T] {
    import valueT.FnValue

    private[this] def evalFn(pn: PackageName, dn: Identifier)(
      implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]],
      valueToTag: Eval[Value[T]] => T[Value[T]]
    ): Eval[FnValue] =
      Eval.now(FnValue(((e1, _) => Eval.defer(e1.map(fn))), externalFnTag(pn, dn)(0, Nil)))
    def call(t: rankn.Type, pn: PackageName, dn: Identifier)(
      implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]],
      valueToTag: Eval[Value[T]] => T[Value[T]]
    ): Eval[Value[T]] = evalFn(pn, dn)
  }
  final case class Fn2[T[_]](fn: (Value[T], Value[T]) => Value[T])(implicit valueT: Evaluation.ValueT[T]) extends FfiCall[T] {
    import valueT.FnValue

    private[this] def evalFn(pn: PackageName, dn: Identifier)(
      implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]],
      valueToTag: Eval[Value[T]] => T[Value[T]]
    ): Eval[FnValue] = {
      val ev =  externalFnTag(pn, dn)
      Eval.now(FnValue(ev(0, Nil)) { (e1, _) =>
        Eval.now(FnValue(ev(1, List(valueToTag(e1)))) { (e2, _) =>
          Eval.defer((e1, e2).mapN(fn(_, _)))
        })
      })
    }

    def call(t: rankn.Type, pn: PackageName, dn: Identifier)(
      implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]],
      valueToTag: Eval[Value[T]] => T[Value[T]]
    ): Eval[Value[T]] = evalFn(pn, dn)
  }
  final case class Fn3[T[_]](fn: (Value[T], Value[T], Value[T]) => Value[T])(implicit valueT: Evaluation.ValueT[T]) extends FfiCall[T] {
    import valueT.FnValue

    private[this] def evalFn(pn: PackageName, dn: Identifier)(
      implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]],
      valueToTag: Eval[Value[T]] => T[Value[T]]
    ): Eval[FnValue] = {
      val ev = externalFnTag(pn, dn)
      Eval.now(FnValue(ev(0, Nil)) { (e1, _) =>
        Eval.now(FnValue(ev(1, List(e1).map(valueToTag))) { (e2, _) =>
          Eval.now(FnValue(ev(2, List(e2, e1).map(valueToTag)))  { (e3, _) =>
            Eval.defer((e1, e2, e3).mapN(fn(_, _, _)))
          })
        })
      })
    }

    def call(t: rankn.Type, pn: PackageName, dn: Identifier)(
      implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]],
      valueToTag: Eval[Value[T]] => T[Value[T]]
    ): Eval[Value[T]] = evalFn(pn, dn)
  }

/*
  final case class RawFn3[T[_]](
    fn: ((Eval[Value[T]], T[Value[T]]), (Eval[Value[T]], T[Value[T]]), (T[Value[T]], T[Value[T]])) => Value[T]
    )(implicit valueT: Evaluation.ValueT[T]) extends FfiCall[T] {
    import valueT.FnValue

    private[this] def evalFn(pn: PackageName, dn: Identifier)(
      implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]]
    ): Eval[FnValue] = ???

    def call(t: rankn.Type, pn: PackageName, dn: Identifier)(
      implicit externalFnTag: (PackageName, Identifier) => (Int, List[T[Value[T]]]) => T[Value[T]]
    ): Eval[Value[T]] = ???
  }
*/

  def getJavaType[T[_]](t: rankn.Type): List[Class[_]] = {
    def loop(t: rankn.Type, top: Boolean): List[Class[_]] = {
      t match {
        case rankn.Type.Fun(a, b) if top =>
          loop(a, false) match {
            case at :: Nil => at :: loop(b, top)
            case function => sys.error(s"unsupported function type $function in $t")
          }
        case rankn.Type.ForAll(_, t) =>
          loop(t, top)
        case _ => classOf[Evaluation.Value[T]] :: Nil
      }
    }
    loop(t, true)
  }
}

case class Externals[T[_]](toMap: Map[(PackageName, String), FfiCall[T]]) {
  def add(pn: PackageName, value: String, f: FfiCall[T]): Externals[T] =
    Externals(toMap + ((pn, value) -> f))

  def ++(that: Externals[T]): Externals[T] =
    Externals(toMap ++ that.toMap)
}

object Externals {
  def empty[T[_]]: Externals[T] = Externals(Map.empty)
}
