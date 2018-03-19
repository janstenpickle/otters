package otters

import cats.effect.{Effect, IO}
import _root_.fs2.{Stream => Fs2Stream}
import cats.Monad

trait Fs2TestBase
    extends TestBase[Fs2Stream[IO, ?], IO, IO, FunctionPipe[Fs2Stream[IO, ?], ?, ?], FunctionSink[
      Fs2Stream[IO, ?],
      IO,
      ?,
      ?
    ]] {

  override implicit def F: EitherStream[
    fs2.Stream[IO, ?],
    IO,
    IO,
    FunctionPipe[fs2.Stream[IO, ?], ?, ?],
    FunctionSink[fs2.Stream[IO, ?], IO, ?, ?]
  ] =
    otters.instances.fs2.fs2instances

  override def mkPipe[A, B](f: A => B): FunctionPipe[Fs2Stream[IO, ?], A, B] = _.map(f)

  override def mkSeqSink[A]: FunctionSink[Fs2Stream[IO, ?], IO, A, IO[Seq[A]]] = _.compile.toList.map(IO(_))

  override def runStream[A](stream: Fs2Stream[IO, A]): Seq[A] = waitFor(materialize(mkSeqSink(stream)))

  override def materialize[A](i: IO[A]): A = waitFor(i)

  override def waitFor[A](fut: IO[A]): A = fut.unsafeRunSync()

  override implicit def G: Monad[IO] = implicitly[Effect[IO]]
}
