package org.bykn.bosatsu

import cats.data.NonEmptyList
import com.stripe.dagon.Memoize
import cats.Eval
import cats.implicits._

case class Normalization(pm: PackageMap.Inferred) {

  def normalizeLast(p: PackageName): Option[(Ref, Scheme)] =
    for {
      pack <- pm.toMap.get(p)
      (_, expr) <- pack.program.lets.lastOption
    } yield {
      norm((Package.asInferred(pack), Right(expr.mapTag(_._2)), Map.empty))
    }
  
  private type Ref = Either[String, Expr[Scheme]]

  private def normExpr(p: Package.Inferred,
    expr: Expr[Scheme],
    env: Map[String, Expr[Scheme]],
    recurse: ((Package.Inferred, Ref, Map[String, Expr[Scheme]])) => (Ref, Scheme)): (Ref, Scheme) = {

    import Expr._

    expr match {
      case Var(v, scheme) =>
        env.get(v) match {
          case Some(a) => (Right(a), scheme)
          case None => recurse((p, Left(v), env))
        }
      case App(Lambda(name, fn, _), arg, scheme) => {
        val localEnv: Map[String, Expr[Scheme]] = Map(name -> arg) 
        recurse((p, Right(fn), env ++ localEnv))
      }
      case App(fn, arg, scheme) => {
        val efn = recurse((p, Right(fn), env))._1
        val earg = recurse((p, Right(arg), env))._1
        efn match {
          case Right(lam @ Lambda(_, _, _)) => recurse((p, Right(App(lam, earg.right.get, scheme)), env))
          case _ => (Right(App(efn.right.get, arg, scheme)), scheme)
        }
      }
      case lam @ Lambda(name, expr, scheme) => (Right(lam), scheme)
      case Let(arg, e, in, scheme) => recurse((p, Right(in), env ++ Map(arg -> e)))
      case lit @ Literal(_, scheme) => (Right(lit), scheme)
      case Match(arg, branches, scheme) => ???
    }
  }

  /**
   * We only call this on typechecked names, which means we know
   * that names resolve
   */
  private[this] val norm: ((Package.Inferred, Ref, Map[String, Expr[Scheme]])) => (Ref, Scheme) =
    Memoize.function[(Package.Inferred, Ref, Map[String, Expr[Scheme]]), (Ref, Scheme)] {
      case ((pack, Right(expr), env), recurse) =>
        normExpr(pack, expr, env, recurse)
      case ((pack, Left(item), env), recurse) =>
        NameKind(pack, item).get match { // this get should never fail due to type checking
          case NameKind.Let(expr) =>
            recurse((pack, Right(expr.mapTag(_._2)), env))
          case NameKind.Constructor(cn, dt, schm) => ???
          case NameKind.Import(from, orig) =>
            // we reset the environment in the other package
            recurse((from, Left(orig), Map.empty))
          case NameKind.ExternalDef(pn, n, scheme) => (Right(Expr.Var(item, scheme)), scheme)
        }
    }
}
