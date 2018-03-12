package otters.laws

import cats.laws._
import cats.kernel.laws.IsEq
import otters.AsyncStream

import cats.syntax.functor._

trait AsyncStreamLaws[F[_], G[_]] {
  implicit def F: AsyncStream[F, G]

  def mapAsyncAssociativity[A, B](fa: F[A], f: A => B, gb: B => G[B]): IsEq[F[B]] =
    fa.map(f) <-> F.mapAsync(fa)(a => gb(f(a)))

  def mapAsyncNAssociativity[A, B](fa: F[A], f: A => B, gb: B => G[B]): IsEq[F[B]] =
    fa.map(f) <-> F.mapAsyncN(fa)(4)(a => gb(f(a)))
}

object AsyncStreamLaws {
  def apply[F[_], G[_]](implicit ev: AsyncStream[F, G]): AsyncStreamLaws[F, G] = new AsyncStreamLaws[F, G] {
    implicit override def F: AsyncStream[F, G] = ev
  }
}
