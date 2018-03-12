package otters

import cats.{Functor, Semigroupal}
import cats.syntax.functor._

trait TupleStream[F[_], G[_], H[_]] extends StreamSink[F, H] with AsyncStream[F, G] {
  implicit def H: Functor[H] with Semigroupal[H]

  def fanOutFanIn[A, B, C, D](fab: F[(A, B)])(lPipe: Pipe[F, A, C], rPipe: Pipe[F, B, D]): F[(C, D)] = {
    val l = lPipe(map(fab)(_._1))
    val r = rPipe(map(fab)(_._2))

    zip(l)(r)
  }

  def toSinks[A, B, C, D, E](
    fab: F[(A, B)]
  )(lSink: Sink[F, H, A, C], rSink: Sink[F, H, B, D])(combine: (C, D) => E): H[E] = {
    val l = lSink(map(fab)(_._1))
    val r = rSink(map(fab)(_._2))

    H.product(l, r).map(combine.tupled)
  }

  def leftVia[A, B, C](fab: F[(A, B)])(lPipe: Pipe[F, A, C]): F[(C, B)] = fanOutFanIn(fab)(lPipe, identity)

  def rightVia[A, B, C](fab: F[(A, B)])(rPipe: Pipe[F, B, C]): F[(A, C)] = fanOutFanIn(fab)(identity, rPipe)
}
