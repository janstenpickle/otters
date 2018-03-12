package otters.laws

import cats.laws._
import cats.syntax.apply._
import cats.syntax.functor._
import otters.{Pipe, Stream}
import otters.syntax.stream._

import scala.concurrent.duration._

trait StreamLaws[F[_]] extends ApplicativeLaws[F] {
  implicit override def F: Stream[F]

  def flatMapAssociativity[A, B, C](fa: F[A], f: A => F[B], g: B => F[C]): IsEq[F[C]] =
    fa.flatMap(f).flatMap(g) <-> fa.flatMap(a => f(a).flatMap(g))

  def flatMapConsistentApply[A, B](fa: F[A], fab: F[A => B]): IsEq[F[B]] =
    fab.ap(fa) <-> fab.flatMap(f => fa.map(f))

  def mapConcatAssociativity[A, B, C](fa: F[A], f: A => List[B], g: B => List[C]): IsEq[F[C]] =
    fa.mapConcat(f).mapConcat(g) <-> fa.mapConcat(a => f(a).flatMap(g))

  def pipeCovariantComposition[A, B, C](fa: F[A], fab: Pipe[F, A, B], fbc: Pipe[F, B, C]): IsEq[F[C]] =
    fa.via(fab).via(fbc) <-> fa.via(fab.andThen(fbc))

  def zipHomomorphism[A, B](abs: List[(A, B)]): IsEq[F[(A, B)]] =
    F.fromSeq(abs.map(_._1)).zip(F.fromSeq(abs.map(_._2))) <-> F.fromSeq(abs)

  def groupedHomomorphism[A](as: List[A]): IsEq[F[Seq[A]]] =
    F.fromSeq(as).grouped(2) <-> F.fromSeq(as.grouped(2).map(_.toSeq).toSeq)

  def groupedWithinHomomorphism[A](as: List[A]): IsEq[F[Seq[A]]] =
    F.fromSeq(as).groupedWithin(2, 1.second) <-> F.fromSeq(as.grouped(2).map(_.toSeq).toSeq)

}

object StreamLaws {
  def apply[F[_]](implicit ev: Stream[F]): StreamLaws[F] = new StreamLaws[F] {
    implicit override def F: Stream[F] = ev
  }
}
