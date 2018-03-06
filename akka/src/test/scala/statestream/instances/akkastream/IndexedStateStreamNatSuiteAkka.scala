package statestream

import akka.stream.scaladsl.RunnableGraph
import cats.effect.{Effect, IO}
import cats.kernel.Monoid
import cats.{~>, Monad}
import statestream.instances.akkastream.Src

import scala.concurrent.Future

class IndexedStateStreamNatSuiteAkka extends IndexedStateStreamAkkaSuite[IO] {

  override implicit def G: Monad[IO] = implicitly[Effect[IO]]
  override implicit def nat: ~>[IO, Future] = new ~>[IO, Future] {
    override def apply[A](fa: IO[A]): Future[A] = fa.unsafeToFuture()
  }

  override def extract[A](fa: IO[A]): A = fa.unsafeRunSync()

  override def mkStateStream[S, A](src: Src[A]): IndexedStateStream[Src, IO, Future, RunnableGraph, S, S, A] =
    IndexedStateStreamNat(src)

  override def mkStateStream[S: Monoid, A](
    src: Src[(S, A)]
  ): IndexedStateStream[Src, IO, Future, RunnableGraph, S, S, A] = IndexedStateStreamNat(src)

}
