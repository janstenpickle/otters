package statestream

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.kernel.Monoid
import cats.{Monad, ~>}

import scala.concurrent.Future

import IndexedStateSourceNat._

class IndexedStateSourceNatSuite extends IndexedStateSourceSuite[Future] {
  override implicit def F: Monad[Future] = cats.instances.all.catsStdInstancesForFuture

  override implicit def nat: ~>[Future, Future] = new (Future ~> Future) {
    override def apply[A](fa: Future[A]): Future[A] = fa
  }

  override def mkStateSource[S, A](src: Source[A, NotUsed]): IndexedStateSource[Future, S, S, A] =
    src.toStateSource

  override def mkStateSource[S: Monoid, A](src: Source[(S, A), NotUsed]): IndexedStateSource[Future, S, S, A] =
    src.toStateSource

  override def extract[A](fa: Future[A]): A = IndexedStateSourceSuite.waitFor(fa)
}
