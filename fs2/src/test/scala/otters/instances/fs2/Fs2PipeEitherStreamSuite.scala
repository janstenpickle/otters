package otters

import _root_.fs2.{Stream => Fs2Stream}
import cats.data.EitherT
import cats.effect.IO

class Fs2PipeEitherStreamSuite
    extends Fs2PipeTestBase[Int]
    with EitherStreamSuite[FunctionPipe[Fs2Stream[IO, ?], Int, ?], IO, FunctionSink[Fs2Stream[IO, ?], IO, Int, ?], FunctionPipe[
      Fs2Stream[IO, ?],
      ?,
      ?
    ], FunctionSink[Fs2Stream[IO, ?], IO, ?, ?]] {

  import EitherSyntax._

  override def input: Fs2Stream[IO, Int] = Fs2Stream.emit(1)

  override def mkEitherStream[A, B](
    src: FunctionPipe[fs2.Stream[IO, ?], Int, Either[A, B]]
  ): EitherT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], A, B] = src.toEitherT

  override def mkEitherStream[A](
    src: FunctionPipe[fs2.Stream[IO, ?], Int, A],
    isLeft: A => Boolean
  ): EitherT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], A, A] =
    EitherT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], A, A](F.split[A](src)(isLeft))

  override def mkEitherStream[A, B, C](
    src: FunctionPipe[fs2.Stream[IO, ?], Int, A],
    isLeft: A => Boolean,
    f: A => B,
    g: A => C
  ): EitherT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], B, C] =
    EitherT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], B, C](F.split[A, B, C](src)(isLeft, f, g))

  override def mkEitherStreamCatch[A, B](
    src: FunctionPipe[fs2.Stream[IO, ?], Int, A],
    f: A => B
  ): EitherT[FunctionPipe[fs2.Stream[IO, ?], Int, ?], Throwable, B] = src.catchNonFatal(f)
}
