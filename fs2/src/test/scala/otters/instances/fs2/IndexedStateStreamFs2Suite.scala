package otters

import _root_.fs2.{Stream => Fs2Stream}
import cats.Monad
import cats.effect.{Effect, IO}
import org.scalatest.BeforeAndAfterAll

trait IndexedStateStreamFs2Suite[G[_]] extends WriterStreamSuite[Fs2Stream[IO, ?], G, IO, IO] with BeforeAndAfterAll {

  override implicit def H: Monad[IO] = implicitly[Effect[IO]]

  override implicit def F: TupleStream[Fs2Stream[IO, ?], IO, IO] =
    otters.instances.fs2.fs2instances

  override def mkPipe[A, B](f: A => B): Pipe[Fs2Stream[IO, ?], A, B] = _.map(f)

  override def mkSeqSink[A]: Sink[Fs2Stream[IO, ?], IO, A, IO[Seq[A]]] = _.compile.toList.map(IO(_))

  override def runStream[A](stream: Fs2Stream[IO, A]): Seq[A] = waitFor(materialize(mkSeqSink(stream)))

  override def materialize[A](i: IO[A]): A = waitFor(i)

  override def waitFor[A](fut: IO[A]): A = fut.unsafeRunSync()
}
