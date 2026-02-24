package dev.bosatsu

import dev.bosatsu.Identifier.Bindable
import scala.collection.mutable

object NormalizationProvenance {

  final case class ProvenanceId(value: Long) extends AnyVal

  enum Operation derives CanEqual {
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

  final case class Dag[A](nodes: Map[ProvenanceId, Node[A]]) {
    def parentsExist: Boolean =
      nodes.valuesIterator.forall { node =>
        node.parents.forall(nodes.contains)
      }

    def isAcyclic: Boolean = {
      val temp = mutable.HashSet.empty[ProvenanceId]
      val perm = mutable.HashSet.empty[ProvenanceId]

      def visit(id: ProvenanceId): Boolean =
        if (perm(id)) true
        else if (temp(id)) false
        else {
          val _ = temp.add(id)
          val ok = nodes.get(id).forall(_.parents.forall(visit))
          val _ = temp.remove(id)
          if (ok) {
            val _ = perm.add(id)
          }
          ok
        }

      nodes.keysIterator.forall(visit)
    }

    def ancestorsInclusive(roots: Iterable[ProvenanceId]): Set[ProvenanceId] = {
      val seen = mutable.HashSet.empty[ProvenanceId]

      def visit(id: ProvenanceId): Unit =
        if (seen.add(id)) {
          nodes.get(id).foreach { node =>
            node.parents.foreach(visit)
          }
        }

      roots.iterator.foreach(visit)
      seen.toSet
    }
  }

  final case class CoverageReport(
      coveredNodeCount: Int,
      uncoveredNodeCount: Int,
      coveredRegions: Set[Region],
      uncoveredRegions: Set[Region]
  ) {
    def summaryLine: String =
      s"provenance coverage: $coveredNodeCount covered nodes, $uncoveredNodeCount uncovered nodes"
  }

  def coverageFromRoots[A](
      dag: Dag[A],
      roots: Iterable[ProvenanceId],
      regionOf: A => Option[Region]
  ): CoverageReport = {
    val covered = dag.ancestorsInclusive(roots)
    val all = dag.nodes.keySet
    val uncovered = all -- covered

    def regions(ids: Set[ProvenanceId]): Set[Region] =
      ids.iterator.flatMap(id => dag.nodes.get(id).flatMap(n => regionOf(n.tag))).toSet

    CoverageReport(
      coveredNodeCount = covered.size,
      uncoveredNodeCount = uncovered.size,
      coveredRegions = regions(covered),
      uncoveredRegions = regions(uncovered)
    )
  }

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
