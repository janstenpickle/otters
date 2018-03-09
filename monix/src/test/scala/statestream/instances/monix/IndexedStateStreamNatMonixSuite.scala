package statestream

import cats.effect.{Effect, IO}
import cats.kernel.Monoid
import cats.{~>, Monad}
import monix.eval.Task
import monix.reactive.Observable
import org.scalatest.BeforeAndAfterAll

class IndexedStateStreamNatMonixSuite extends IndexedStateStreamMonixSuite[IO] with BeforeAndAfterAll {

  override def mkStateStream[S, A](src: Observable[A]): IndexedStateStream[Observable, IO, Task, Task, S, S, A] =
    IndexedStateStreamNat(src)

  override def mkStateStream[S: Monoid, A](
    src: Observable[(S, A)]
  ): IndexedStateStream[Observable, IO, Task, Task, S, S, A] = IndexedStateStreamNat(src)

  override def extract[A](fa: IO[A]): A = fa.unsafeRunSync()

  override implicit def G: Monad[IO] = implicitly[Effect[IO]]

  override implicit def nat: ~>[IO, Task] = new ~>[IO, Task] {
    override def apply[A](fa: IO[A]): Task[A] = Task.fromIO(fa)
  }
}
