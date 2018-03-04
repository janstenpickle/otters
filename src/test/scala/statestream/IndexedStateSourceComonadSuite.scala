package statestream

import akka.NotUsed
import akka.stream.scaladsl.Source
import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Monad}

import scala.concurrent.Future

import IndexedStateSourceComonad._

class IndexedStateSourceComonadSuite extends IndexedStateSourceSuite[Eval] {
  override implicit def F: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, Future] = StateSource.evalToFutureNat

  override def mkStateSource[S, A](src: Source[A, NotUsed]): IndexedStateSource[Eval, S, S, A] = src.toStateSource

  override def mkStateSource[S: Monoid, A](src: Source[(S, A), NotUsed]): IndexedStateSource[Eval, S, S, A] =
    src.toStateSource

  override def extract[A](fa: Eval[A]): A = fa.value

}
