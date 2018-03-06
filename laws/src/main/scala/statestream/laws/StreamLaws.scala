package statestream.laws

import cats.laws._
import cats.syntax.apply._
import cats.syntax.functor._
import statestream.{Pipe, Stream}
import statestream.syntax.stream._

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

}

object StreamLaws {
  def apply[F[_]](implicit ev: Stream[F]): StreamLaws[F] = new StreamLaws[F] {
    implicit override def F: Stream[F] = ev
  }
}
