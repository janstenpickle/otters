package otters

import cats.effect.{Effect, IO}
import _root_.fs2.{Stream => Fs2Stream}
import cats.Monad

trait Fs2PipeTestBase[I]
    extends TestBase[FunctionPipe[Fs2Stream[IO, ?], I, ?], IO, FunctionSink[Fs2Stream[IO, ?], IO, I, ?], FunctionPipe[
      Fs2Stream[IO, ?],
      ?,
      ?
    ], FunctionSink[Fs2Stream[IO, ?], IO, ?, ?]] {

  implicit val ev = otters.instances.fs2.fs2instances[IO]

  def input: Fs2Stream[IO, I]

  override implicit def F: EitherStream[
    FunctionPipe[fs2.Stream[IO, ?], I, ?],
    IO,
    FunctionSink[fs2.Stream[IO, ?], IO, I, ?],
    FunctionPipe[fs2.Stream[IO, ?], ?, ?],
    FunctionSink[fs2.Stream[IO, ?], IO, ?, ?]
  ] = otters.instances.fs2.fs2PipeInstances[IO, I]

  override def mkPipe[A, B](f: A => B): FunctionPipe[fs2.Stream[IO, ?], A, B] = _.map(f)

  override def mkSeqSink[A]: FunctionSink[fs2.Stream[IO, ?], IO, A, IO[Seq[A]]] = _.compile.toList.map(IO(_))

  override def runStream[A](stream: FunctionPipe[fs2.Stream[IO, ?], I, A]): Seq[A] =
    waitFor(materialize(stream.andThen(mkSeqSink)))

  override def materialize[A](i: FunctionSink[fs2.Stream[IO, ?], IO, I, A]): A = waitFor(i(input))

  override def waitFor[A](fut: IO[A]): A = fut.unsafeRunSync()

  override implicit def G: Monad[IO] = implicitly[Effect[IO]]

}
