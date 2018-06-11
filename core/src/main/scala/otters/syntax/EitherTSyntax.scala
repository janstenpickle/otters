package otters.syntax

import cats.Applicative
import cats.data.EitherT
import cats.syntax.functor._
import otters.{AsyncStream, EitherStream, EitherStreamBase}

trait EitherTSyntax {
  implicit class EitherTStreamOps[F[_], A, B](override val stream: EitherT[F, A, B]) extends EitherTStreamAsync[F, A, B]

  implicit class EitherTStreamApply[F[_], A](override val stream: F[A]) extends EitherTApply[F, A]
  implicit class EitherTStreamApplyEither[F[_], A, B](override val stream: F[Either[A, B]])
      extends EitherTApplyEither[F, A, B]
}

trait EitherTApply[F[_], A] {
  def stream: F[A]

  def split[B, C](isLeft: A => Boolean, f: A => B, g: A => C)(implicit F: EitherStreamBase[F]): EitherT[F, B, C] =
    EitherT(F.split[A, B, C](stream)(isLeft, f, g))

  def split(isLeft: A => Boolean)(implicit F: EitherStreamBase[F]): EitherT[F, A, A] =
    EitherT(F.split[A](stream)(isLeft))

  def catchNonFatal[B](f: A => B)(implicit F: EitherStreamBase[F]): EitherT[F, Throwable, B] =
    EitherT(F.catchNonFatal(stream)(f))
}

trait EitherTApplyEither[F[_], A, B] {
  def stream: F[Either[A, B]]
  def toEitherT: EitherT[F, A, B] = EitherT(stream)
}

trait EitherTExtendedSyntax[P[_, _], S[_, _]] {
  implicit class EitherTStreamExtOps[F[_], A, B](override val stream: EitherT[F, A, B])
      extends EitherTStreamPipeSink[F, P, S, A, B]

  trait AllOps[F[_], A, B] extends EitherTStreamAsync[F, A, B] with EitherTStreamPipeSink[F, P, S, A, B]
}

trait EitherTStreamPipeSink[F[_], P[_, _], S[_, _], A, B] {
  def stream: EitherT[F, A, B]

  def via[G[_], H[_], C, D](lPipe: P[A, C], rPipe: P[B, D])(implicit F: EitherStream[F, G, H, P, S]): EitherT[F, C, D] =
    EitherT(F.via(stream.value)(lPipe, rPipe))

  def leftVia[G[_], H[_], C](lPipe: P[A, C])(implicit F: EitherStream[F, G, H, P, S]): EitherT[F, C, B] =
    EitherT(F.leftVia(stream.value)(lPipe))

  def rightVia[G[_], H[_], C](rPipe: P[B, C])(implicit F: EitherStream[F, G, H, P, S]): EitherT[F, A, C] =
    EitherT(F.rightVia(stream.value)(rPipe))

  def toSinks[G[_], H[_], C, D](lSink: S[A, C], rSink: S[B, D])(implicit F: EitherStream[F, G, H, P, S]): H[(C, D)] =
    F.toEitherSinks[A, B, C, D](stream.value)(lSink, rSink)

  def toSinks[G[_], H[_], C, D, E](lSink: S[A, C], rSink: S[B, D])(
    combine: (C, D) => E
  )(implicit F: EitherStream[F, G, H, P, S]): H[E] =
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
