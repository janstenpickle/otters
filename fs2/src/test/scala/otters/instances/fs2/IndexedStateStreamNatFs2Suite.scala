package otters

import _root_.fs2.{Stream => Fs2Stream}
import cats.effect.IO
import cats.kernel.Monoid
import cats.{~>, Monad}
import org.scalatest.BeforeAndAfterAll
import otters.syntax.fs2.nat._

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class IndexedStateStreamNatFs2Suite extends IndexedStateStreamFs2Suite[Future] with BeforeAndAfterAll {

  override implicit def G: Monad[Future] = cats.instances.future.catsStdInstancesForFuture

  override implicit def nat: ~>[Future, IO] = new ~>[Future, IO] {
    override def apply[A](fa: Future[A]): IO[A] = IO.fromFuture(IO(fa))
  }

  override def extract[A](fa: Future[A]): A = Await.result(fa, Duration.Inf)

  override def mkWriterStream[S, A](src: Fs2Stream[IO, (S, A)]): WriterStream[Fs2Stream[IO, ?], Future, IO, IO, S, A] =
    src.toWriterStream[Future]

  override def mkWriterStream[S: Monoid, A](
    src: Fs2Stream[IO, A]
  ): WriterStream[Fs2Stream[IO, ?], Future, IO, IO, S, A] =
    src.toWriterStream[Future, S]
}
