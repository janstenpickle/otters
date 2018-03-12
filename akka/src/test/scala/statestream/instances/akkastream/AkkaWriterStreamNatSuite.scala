package statestream

import akka.stream.scaladsl.RunnableGraph
import cats.effect.{Effect, IO}
import cats.kernel.Monoid
import cats.{~>, Monad}
import statestream.instances.akkastream.Src
import statestream.syntax.akkastream.nat._

import scala.concurrent.Future

class AkkaWriterStreamNatSuite extends AkkaWriterStreamSuite[IO] {

  override implicit def G: Monad[IO] = implicitly[Effect[IO]]
  override implicit def nat: ~>[IO, Future] = new ~>[IO, Future] {
    override def apply[A](fa: IO[A]): Future[A] = fa.unsafeToFuture()
  }

  override def extract[A](fa: IO[A]): A = fa.unsafeRunSync()

  override def mkWriterStream[S: Monoid, A](src: Src[A]): WriterStream[Src, IO, Future, RunnableGraph, S, A] =
    src.toWriterStream[IO, S]

  override def mkWriterStream[S, A](src: Src[(S, A)]): WriterStream[Src, IO, Future, RunnableGraph, S, A] =
    src.toWriterStream[IO]

}
