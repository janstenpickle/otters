package otters.instances.monix.reactive

import _root_.monix.eval.Task
import _root_.monix.execution.Scheduler
import _root_.monix.reactive.{Consumer, Observable}
import cats.Eq
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.instances.all._
import cats.laws.discipline.arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import otters.laws.AsyncStreamLaws
import otters.laws.discipline.{AsyncStreamTests, TestBase}

import scala.concurrent.Future

class ObservableInstancesTests extends TestBase with TestInstances {
  implicit val scheduler: Scheduler = Scheduler(TestContext())

  implicit val asyncStreamLaws: AsyncStreamLaws[Observable, Task] = AsyncStreamLaws[Observable, Task]

  implicit def observableArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[Observable[A]] =
    Arbitrary(ev.arbitrary.map(as => Observable(as: _*)))

  implicit def observableEq[A](implicit ev: Eq[Future[List[A]]], ec: TestContext): Eq[Observable[A]] = {
    val sched: Scheduler = Scheduler(ec)

    Eq.by(
      obs =>
        obs
          .consumeWith(Consumer.foldLeft(List.empty[A])((acc, v) => v :: acc))
          .runAsync(sched)
          .asInstanceOf[Future[List[A]]]
    )
  }

  implicit def pureTask[A]: Arbitrary[A => Task[A]] = Arbitrary(Gen.const(Task.now[A]))

  checkAllAsync("Observable[Int]", implicit ec => AsyncStreamTests[Observable, Task].asyncStream[Int, Int, Int])
}
