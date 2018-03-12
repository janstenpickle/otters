package otters

import cats.effect.{Effect, IO}
import cats.kernel.Monoid
import cats.{~>, Monad}
import monix.eval.Task
import monix.reactive.Observable
import org.scalatest.BeforeAndAfterAll
import otters.syntax.monix.reactive.nat._

class ObservableWriterNatSuite extends ObservableWriterStreamSuite[IO] with BeforeAndAfterAll {

  override def mkWriterStream[S: Monoid, A](src: Observable[A]): WriterStream[Observable, IO, Task, Task, S, A] =
    src.toWriterStream[IO, S]

  override def mkWriterStream[S, A](src: Observable[(S, A)]): WriterStream[Observable, IO, Task, Task, S, A] =
    src.toWriterStream[IO]

  override def extract[A](fa: IO[A]): A = fa.unsafeRunSync()

  override implicit def G: Monad[IO] = implicitly[Effect[IO]]

  override implicit def nat: ~>[IO, Task] = new ~>[IO, Task] {
    override def apply[A](fa: IO[A]): Task[A] = Task.fromIO(fa)
  }
}
