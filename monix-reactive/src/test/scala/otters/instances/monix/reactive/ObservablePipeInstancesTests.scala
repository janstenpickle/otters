package otters.instances.monix.reactive

import _root_.monix.eval.Task
import _root_.monix.execution.Scheduler
import _root_.monix.reactive.{Consumer, Observable}
import cats.Eq
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.instances.all._
import cats.laws.IsEq
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import cats.laws.discipline.arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import otters._
import otters.laws.AsyncStreamLaws
import otters.laws.discipline.{AsyncStreamTests, TestBase}

import scala.concurrent.Future

class ObservablePipeInstancesTests extends TestBase with TestInstances {
  type TestPipe[A] = FunctionPipe[Observable, Int, A]

  implicit val scheduler: Scheduler = Scheduler(TestContext())

  implicit val asyncStreamLaws: AsyncStreamLaws[TestPipe, Task] = AsyncStreamLaws[TestPipe, Task]

  implicit def pipeArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[TestPipe[A]] =
    Arbitrary(ev.arbitrary.map(as => (_: Observable[Int]) => Observable(as: _*)))

  implicit def observableEq[A](implicit ev: Eq[Future[List[A]]], ec: TestContext): Eq[TestPipe[A]] = {
    val sched: Scheduler = Scheduler(ec)

    Eq.by(
      obs =>
        obs(Observable.pure(1))
          .consumeWith(Consumer.foldLeft(List.empty[A])((acc, v) => v :: acc))
          .runAsync(sched)
          .asInstanceOf[Future[List[A]]]
    )
  }

  implicit def pureTask[A]: Arbitrary[A => Task[A]] = Arbitrary(Gen.const(Task.now[A]))

  implicit def iso(implicit ev: Isomorphisms[Observable]): Isomorphisms[TestPipe] = new Isomorphisms[TestPipe] {
    override def associativity[A, B, C](
      fs: (TestPipe[(A, (B, C))], TestPipe[((A, B), C)])
    ): IsEq[TestPipe[(A, B, C)]] = {
      val (l, r) = fs
      val obs = Observable.pure(1)
      val iseq = ev.associativity((l(obs), r(obs)))
      IsEq((_: Observable[Int]) => iseq.lhs, (_: Observable[Int]) => iseq.rhs)
    }

    override def leftIdentity[A](fs: (TestPipe[(Unit, A)], TestPipe[A])): IsEq[TestPipe[A]] = {
      val (l, r) = fs
      val obs = Observable.pure(1)
      val iseq = ev.leftIdentity((l(obs), r(obs)))
      IsEq((_: Observable[Int]) => iseq.lhs, (_: Observable[Int]) => iseq.rhs)
    }

    override def rightIdentity[A](fs: (TestPipe[(A, Unit)], TestPipe[A])): IsEq[TestPipe[A]] = {
      val (l, r) = fs
      val obs = Observable.pure(1)
      val iseq = ev.rightIdentity((l(obs), r(obs)))
      IsEq((_: Observable[Int]) => iseq.lhs, (_: Observable[Int]) => iseq.rhs)
    }
  }

  checkAllAsync(
    "Observable[Int] => Observable[Int]",
    implicit ec => AsyncStreamTests[TestPipe, Task].asyncStream[Int, Int, Int]
  )
}
