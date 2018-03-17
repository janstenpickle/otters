package otters.syntax

import cats.Applicative
import cats.data.EitherT
import cats.syntax.functor._
import otters.{AsyncStream, EitherStream, Pipe, Sink}

trait EitherTSyntax {
  implicit class EitherTStreamOps[F[_], A, B](override val stream: EitherT[F, A, B])
      extends EitherTStreamAsync[F, A, B]
      with EitherTStreamPipeSink[F, A, B]

  implicit class EitherTStreamApply[F[_], G[_], H[_], A](stream: F[A]) {
    def split[B, C](isLeft: A => Boolean, f: A => B, g: A => C)(implicit F: EitherStream[F, G, H]): EitherT[F, B, C] =
      EitherT(F.split[A, B, C](stream)(isLeft, f, g))

    def split(isLeft: A => Boolean)(implicit F: EitherStream[F, G, H]): EitherT[F, A, A] =
      EitherT(F.split[A](stream)(isLeft))

    def catchNonFatal[B](f: A => B)(implicit F: EitherStream[F, G, H]): EitherT[F, Throwable, B] =
      EitherT(F.catchNonFatal(stream)(f))
  }

  implicit class EitherTStreamApplyEither[F[_], G[_], H[_], A, B](stream: F[Either[A, B]]) {
    def toEitherT: EitherT[F, A, B] = EitherT(stream)
  }
}

trait EitherTStreamPipeSink[F[_], A, B] {
  def stream: EitherT[F, A, B]

  def via[G[_], H[_], C, D](lPipe: Pipe[F, A, C], rPipe: Pipe[F, B, D])(
    implicit F: EitherStream[F, G, H]
  ): EitherT[F, C, D] =
    EitherT(F.via(stream.value)(lPipe, rPipe))

  def leftVia[G[_], H[_], C](lPipe: Pipe[F, A, C])(implicit F: EitherStream[F, G, H]): EitherT[F, C, B] =
    via(lPipe, identity)
  def rightVia[G[_], H[_], C](rPipe: Pipe[F, B, C])(implicit F: EitherStream[F, G, H]): EitherT[F, A, C] =
    via(identity, rPipe)

  def toSinks[G[_], H[_], C, D, E](lSink: Sink[F, H, A, C], rSink: Sink[F, H, B, D])(
    combine: (C, D) => E
  )(implicit F: EitherStream[F, G, H]): H[E] =
    F.toEitherSinks[A, B, C, D, E](stream.value)(lSink, rSink)(combine)
}

trait EitherTStreamAsync[F[_], A, B] {
  def stream: EitherT[F, A, B]

  def flatMapAsync[G[_], C](
    parallelism: Int
  )(f: B => EitherT[G, A, C])(implicit F: AsyncStream[F, G], G: Applicative[G]): EitherT[F, A, C] =
    EitherT(F.mapAsyncN(stream.value)(parallelism)(_.fold(a => G.pure(Left(a)), f(_).value)))

  def flatMapAsync[G[_], C](
    f: B => EitherT[G, A, C]
  )(implicit F: AsyncStream[F, G], G: Applicative[G]): EitherT[F, A, C] =
    EitherT(F.mapAsync(stream.value)(_.fold(a => G.pure(Left(a)), f(_).value)))

  def flatLeftMapAsync[G[_], C](
    parallelism: Int
  )(f: A => EitherT[G, C, B])(implicit F: AsyncStream[F, G], G: Applicative[G]): EitherT[F, C, B] =
    EitherT(F.mapAsyncN(stream.value)(parallelism)(_.fold(f(_).value, b => G.pure(Right(b)))))

  def flatLeftMapAsync[G[_], C](
    f: A => EitherT[G, C, B]
  )(implicit F: AsyncStream[F, G], G: Applicative[G]): EitherT[F, C, B] =
    EitherT(F.mapAsync(stream.value)(_.fold(f(_).value, b => G.pure(Right(b)))))

  def mapAsync[G[_], C](
    parallelism: Int
  )(f: B => G[C])(implicit F: AsyncStream[F, G], G: Applicative[G]): EitherT[F, A, C] =
    EitherT(F.mapAsyncN(stream.value)(parallelism)(_.fold[G[Either[A, C]]](a => G.pure(Left(a)), f(_).map(Right(_)))))

  def mapAsync[G[_], C](f: B => G[C])(implicit F: AsyncStream[F, G], G: Applicative[G]): EitherT[F, A, C] =
    EitherT(F.mapAsync(stream.value)(_.fold[G[Either[A, C]]](a => G.pure(Left(a)), f(_).map(Right(_)))))

  def leftMapAsync[G[_], C](
    parallelism: Int
  )(f: A => G[C])(implicit F: AsyncStream[F, G], G: Applicative[G]): EitherT[F, C, B] =
    EitherT(F.mapAsyncN(stream.value)(parallelism)(_.fold[G[Either[C, B]]](f(_).map(Left(_)), b => G.pure(Right(b)))))

  def leftMapAsync[G[_], C](f: A => G[C])(implicit F: AsyncStream[F, G], G: Applicative[G]): EitherT[F, C, B] =
    EitherT(F.mapAsync(stream.value)(_.fold[G[Either[C, B]]](f(_).map(Left(_)), b => G.pure(Right(b)))))
}
