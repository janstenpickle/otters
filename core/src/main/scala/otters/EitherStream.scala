package otters

import cats.syntax.functor._
import cats.syntax.either._

trait EitherStream[F[_], G[_], H[_]] extends TupleStream[F, G, H] {
  def catchNonFatal[A, B](fa: F[A])(f: A => B): F[Either[Throwable, B]] =
    map(fa)(a => Either.catchNonFatal(f(a)))

  def split[A, B, C](fa: F[A])(isLeft: A => Boolean, f: A => B, g: A => C): F[Either[B, C]] =
    map(fa)(
      a =>
        if (isLeft(a)) Left(f(a))
        else Right(g(a))
    )

  def split[A](fa: F[A])(isLeft: A => Boolean): F[Either[A, A]] = split[A, A, A](fa)(isLeft, identity, identity)

  def via[A, B, C, D](fa: F[Either[A, B]])(lPipe: Pipe[F, A, C], rPipe: Pipe[F, B, D]): F[Either[C, D]] =
    flatMap(fa) {
      case Left(a) => map(lPipe(pure(a)))(Left(_))
      case Right(b) => map(rPipe(pure(b)))(Right(_))
    }

  def leftVia[A, B, C](fa: F[Either[A, B]])(lPipe: Pipe[F, A, C]): F[Either[C, B]] = via(fa)(lPipe, identity)
  def rightVia[A, B, C](fa: F[Either[A, B]])(rPipe: Pipe[F, B, C]): F[Either[A, C]] = via(fa)(identity, rPipe)

  def toEitherSinks[A, B, C, D](fab: F[Either[A, B]])(lSink: Sink[F, H, A, C], rSink: Sink[F, H, B, D]): H[(C, D)] = {
    val left = lSink(collect(fab) { case Left(a) => a })
    val right = rSink(collect(fab) { case Right(b) => b })

    H.product(left, right)
  }

  def toEitherSinks[A, B, C, D, E](
    fab: F[Either[A, B]]
  )(lSink: Sink[F, H, A, C], rSink: Sink[F, H, B, D])(combine: (C, D) => E): H[E] =
    toEitherSinks[A, B, C, D](fab)(lSink, rSink).map(combine.tupled)
}
