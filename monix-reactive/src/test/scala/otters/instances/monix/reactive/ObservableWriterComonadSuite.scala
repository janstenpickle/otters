package otters

import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Monad}
import monix.eval.Task
import monix.reactive.Observable
import otters.syntax.monix.reactive.comonad._

class ObservableWriterComonadSuite extends ObservableWriterStreamSuite[Eval] {

  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, Task] = new ~>[Eval, Task] {
    override def apply[A](fa: Eval[A]): Task[A] = Task.fromEval(fa)
  }

  override def mkWriterStream[S: Monoid, A](src: Observable[A]): WriterStream[Observable, Eval, Task, Task, S, A] =
    src.toWriterStream[Eval, S]

  override def mkWriterStream[S, A](src: Observable[(S, A)]): WriterStream[Observable, Eval, Task, Task, S, A] =
    src.toWriterStream[Eval]

  override def extract[A](fa: Eval[A]): A = fa.value
}
