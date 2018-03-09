package statestream.instances.monix

import _root_.monix.eval.Task
import _root_.monix.execution.Scheduler
import _root_.monix.reactive.{Consumer, Observable}
import cats.Eq
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.instances.all._
import cats.laws.discipline.arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import statestream.laws.{AsyncStreamLaws, TupleStreamLaws}
import statestream.laws.discipline.{AsyncStreamTests, TestBase, TupleStreamTests}
import statestream.{Pipe, Sink}

import scala.concurrent.Future

class MonixInstancesTests extends TestBase with TestInstances {
  implicit val streamLaws: TupleStreamLaws[Observable, Task, Task] = TupleStreamLaws[Observable, Task, Task]
  implicit val asyncStreamLaws: AsyncStreamLaws[Observable, Task] = AsyncStreamLaws[Observable, Task]

  implicit def observableArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[Observable[A]] =
    Arbitrary(ev.arbitrary.map(as => Observable(as: _*)))

  implicit def pipeArb[A, B](implicit ev: Arbitrary[A => B]): Arbitrary[Pipe[Observable, A, B]] =
    Arbitrary(ev.arbitrary.map(f => (s: Observable[A]) => s.map(f)))

  implicit def sinkFnArb[A, B](
    implicit ev: Arbitrary[A => B],
    ec: TestContext
  ): Arbitrary[Sink[Observable, Task, A, Task[List[B]]]] =
    Arbitrary(
      ev.arbitrary
        .map(
          f =>
            (s: Observable[A]) =>
              s.map(f)
                .consumeWith(Consumer.foldLeft[Task[List[B]], B](Task.now(List.empty[B]))((acc, v) => acc.map(v :: _)))
        )
    )

  implicit def sinkArb[A](implicit ec: TestContext): Arbitrary[Sink[Observable, Task, A, Task[List[A]]]] =
    Arbitrary(
      Gen.const(
        (s: Observable[A]) =>
          s.consumeWith(Consumer.foldLeft[Task[List[A]], A](Task.now(List.empty[A]))((acc, v) => acc.map(v :: _)))
      )
    )

  implicit def observableEq[A](implicit ev: Eq[Future[List[A]]], ec: TestContext): Eq[Observable[A]] = {
    implicit val sched: Scheduler = Scheduler(ec)

    Eq.by(
      obs =>
        obs.consumeWith(Consumer.foldLeft(List.empty[A])((acc, v) => v :: acc)).runAsync.asInstanceOf[Future[List[A]]]
    )
  }

  implicit def taskEq[A: Eq](implicit ec: TestContext): Eq[Task[A]] = {
    implicit val sched: Scheduler = Scheduler(ec)

    Eq.by(_.runAsync.asInstanceOf[Future[A]])
  }

  implicit def pureTask[A]: Arbitrary[A => Task[A]] = Arbitrary(Gen.const(Task.now[A]))

  checkAllAsync("Observable[Int]", implicit ec => TupleStreamTests[Observable, Task, Task].tupleStream[Int, Int, Int])
  checkAllAsync("Observable[Int]", implicit ec => AsyncStreamTests[Observable, Task].asyncStream[Int, Int])
}
