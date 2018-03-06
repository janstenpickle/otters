package statestream

import akka.stream.scaladsl.RunnableGraph
import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Monad}
import org.scalatest.BeforeAndAfterAll
import statestream.instances.akkastream.Src

import scala.concurrent.Future

class IndexedStateStreamComonadSuiteAkka extends IndexedStateStreamAkkaSuite[Eval] with BeforeAndAfterAll {

  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, Future] = new ~>[Eval, Future] {
    override def apply[A](fa: Eval[A]): Future[A] = Future(fa.value)
  }

  override def extract[A](fa: Eval[A]): A = fa.value

  override def mkStateStream[S, A](src: Src[A]): IndexedStateStream[Src, Eval, Future, RunnableGraph, S, S, A] =
    IndexedStateStreamComonad(src)

  override def mkStateStream[S: Monoid, A](
    src: Src[(S, A)]
  ): IndexedStateStream[Src, Eval, Future, RunnableGraph, S, S, A] = IndexedStateStreamComonad(src)

}
