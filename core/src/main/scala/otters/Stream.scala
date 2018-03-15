package otters

import cats.Monad
import simulacrum.typeclass

import scala.collection.immutable
import scala.concurrent.duration.FiniteDuration

@typeclass trait Stream[F[_]] extends Monad[F] {
  def grouped[A](fa: F[A])(count: Int): F[Seq[A]]
  def groupedWithin[A](fa: F[A])(count: Int, timespan: FiniteDuration): F[Seq[A]]
  def mapConcat[A, B](fa: F[A])(f: A => immutable.Iterable[B]): F[B]
  def fromIterator[A](iter: => Iterator[A]): F[A]
  def fromSeq[A](seq: Seq[A]): F[A]
  def via[A, B](fa: F[A])(pipe: Pipe[F, A, B]): F[B]
  def zip[A, B](fa: F[A])(fb: F[B]): F[(A, B)]
  def collect[A, B](fa: F[A])(pf: PartialFunction[A, B]): F[B]
}
