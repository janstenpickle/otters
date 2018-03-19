package otters

import _root_.fs2.{Stream => Fs2Stream}
import cats.data.EitherT
import cats.effect.IO

class Fs2EitherStreamSuite
    extends Fs2TestBase
    with EitherStreamSuite[Fs2Stream[IO, ?], IO, IO, FunctionPipe[Fs2Stream[IO, ?], ?, ?], FunctionSink[
      Fs2Stream[IO, ?],
      IO,
      ?,
      ?
    ]] {

  import EitherSyntax._

  override def mkEitherStream[A, B](src: Fs2Stream[IO, Either[A, B]]): EitherT[fs2.Stream[IO, ?], A, B] = src.toEitherT

  override def mkEitherStream[A](src: Fs2Stream[IO, A], isLeft: A => Boolean): EitherT[fs2.Stream[IO, ?], A, A] =
    EitherT(F.split[A](src)(isLeft))

  override def mkEitherStream[A, B, C](
    src: Fs2Stream[IO, A],
    isLeft: A => Boolean,
    f: A => B,
    g: A => C
  ): EitherT[fs2.Stream[IO, ?], B, C] = EitherT(F.split[A, B, C](src)(isLeft, f, g))

  override def mkEitherStreamCatch[A, B](src: Fs2Stream[IO, A], f: A => B): EitherT[fs2.Stream[IO, ?], Throwable, B] =
    src.catchNonFatal(f)
}
