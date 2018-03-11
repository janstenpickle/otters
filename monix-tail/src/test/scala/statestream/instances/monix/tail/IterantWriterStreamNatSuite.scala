package statestream

import cats.kernel.Monoid
import cats.{~>, Monad}
import monix.eval.Task
import monix.tail.Iterant

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

class IterantWriterStreamNatSuite extends IterantWriterStreamIterantSuite[Future] {

  override implicit def G: Monad[Future] = cats.instances.future.catsStdInstancesForFuture
  override implicit def nat: ~>[Future, Task] = new ~>[Future, Task] {
    override def apply[A](fa: Future[A]): Task[A] = Task.fromFuture(fa)
  }

  override def mkWriterStream[S: Monoid, A](
    src: Iterant[Task, A]
  ): WriterStream[Iterant[Task, ?], Future, Task, Task, S, A] =
    WriterStreamNat(src)

  override def mkWriterStream[S, A](
    src: Iterant[Task, (S, A)]
  ): WriterStream[Iterant[Task, ?], Future, Task, Task, S, A] = WriterStreamNat(src)

  override def extract[A](fa: Future[A]): A = Await.result(fa, Duration.Inf)
}
