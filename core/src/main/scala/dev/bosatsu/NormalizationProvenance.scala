package dev.bosatsu

import dev.bosatsu.Identifier.Bindable
import scala.collection.mutable

object NormalizationProvenance {

  final case class ProvenanceId(value: Long) extends AnyVal

  enum Operation {
    case Original
    case NormalizeStep
    case CallSiteInline
    case LetInline
    case MatchRewrite
    case LoopRewrite
    case TailRecRewrite
    case ClosureRewrite
    case StructuralShare
  }

  final case class Node[A](
      id: ProvenanceId,
      op: Operation,
      tag: A,
      parents: List[ProvenanceId]
  )

  final case class Dag[A](nodes: Map[ProvenanceId, Node[A]])

  final class Builder[A] private () {
    private val ids =
      new java.util.IdentityHashMap[TypedExpr[A], ProvenanceId]()
    private val nodes = mutable.LinkedHashMap.empty[ProvenanceId, Node[A]]
    private var nextId = 1L

    private def allocId(): ProvenanceId = {
      val id = ProvenanceId(nextId)
      nextId = nextId + 1L
      id
    }

    private def childrenOf(te: TypedExpr[A]): List[TypedExpr[A]] =
      te match {
        case TypedExpr.Generic(_, in) =>
          in :: Nil
        case TypedExpr.Annotation(in, _, _) =>
          in :: Nil
        case TypedExpr.AnnotatedLambda(_, in, _) =>
          in :: Nil
        case TypedExpr.Local(_, _, _) | TypedExpr.Global(_, _, _, _) |
            TypedExpr.Literal(_, _, _) =>
          Nil
        case TypedExpr.App(fn, args, _, _) =>
          fn :: args.toList
        case TypedExpr.Let(_, expr, in, _, _) =>
          expr :: in :: Nil
        case TypedExpr.Loop(args, body, _) =>
          args.toList.map(_._2) ::: (body :: Nil)
        case TypedExpr.Recur(args, _, _) =>
          args.toList
        case TypedExpr.Match(arg, branches, _) =>
          arg :: branches.toList.flatMap { branch =>
            branch.guard.toList ::: (branch.expr :: Nil)
          }
      }

    def idOf(te: TypedExpr[A]): Option[ProvenanceId] =
      Option(ids.get(te))

    def seedTree(te: TypedExpr[A]): ProvenanceId =
      idOf(te).getOrElse {
        childrenOf(te).foreach(seedTree)
        val id = allocId()
        ids.put(te, id)
        nodes.update(id, Node(id, Operation.Original, te.tag, Nil))
        id
      }

    def derive(
        op: Operation,
        result: TypedExpr[A],
        parents: List[TypedExpr[A]]
    ): ProvenanceId = {
      val parentIds = parents.map(seedTree)
      val id = idOf(result).getOrElse {
        val fresh = allocId()
        ids.put(result, fresh)
        fresh
      }
      nodes.update(id, Node(id, op, result.tag, parentIds))
      id
    }

    def rootsFor(
        lets: List[(Bindable, RecursionKind, TypedExpr[A])]
    ): Map[Bindable, ProvenanceId] =
      lets.iterator.map { case (bindable, _, expr) =>
        (bindable, seedTree(expr))
      }.toMap

    def toDag: Dag[A] = Dag(nodes.toMap)
  }

  object Builder {
    def empty[A]: Builder[A] = new Builder[A]()
  }
}
