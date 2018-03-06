package statestream

import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Monad}
import monix.eval.Task
import monix.reactive.Observable

class IndexedStateStreamComonadMonixSuite extends IndexedStateStreamMonixSuite[Eval] {

  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, Task] = new ~>[Eval, Task] {
    override def apply[A](fa: Eval[A]): Task[A] = Task.fromEval(fa)
  }

  override def mkStateStream[S, A](src: Observable[A]): IndexedStateStream[Observable, Eval, Task, Task, S, S, A] =
    IndexedStateStreamComonad(src)

  override def mkStateStream[S: Monoid, A](
    src: Observable[(S, A)]
  ): IndexedStateStream[Observable, Eval, Task, Task, S, S, A] = IndexedStateStreamComonad(src)

  override def extract[A](fa: Eval[A]): A = fa.value
}
