package org.bykn.edgemar

import cats.data.NonEmptyList
import fastparse.all._

object Predef {
  private def resourceToString(path: String): Option[String] = {
    Option(getClass().getResourceAsStream(path)).map { stream =>
      scala.io.Source.fromInputStream(stream)("UTF-8").mkString
    }
  }

  private val predefString: String =
    resourceToString("edgemar/predef.edgemar")
      .getOrElse {
        scala.io.Source.fromFile("target/scala-2.12/classes/edgemar/predef.edgemar").mkString
      }

  val predefPackage: Package.Parsed =
    Package.parser.parse(predefString) match {
      case Parsed.Success(pack, _) => pack
      case Parsed.Failure(_, _, _) => sys.error(s"could not parse $predefString")
    }

  val predefImports: Import[PackageName, Unit] =
    Import(PackageName(NonEmptyList.of("Edgemar", "Predef")),
      NonEmptyList.of(
        "Option",
        "Some",
        "None")
        .map(ImportedName.OriginalName(_, ())))

  def withPredef(ps: List[Package.Parsed]): List[Package.Parsed] =
    predefPackage :: ps.map(_.withImport(predefImports))
}