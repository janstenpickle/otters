package otters.instances.monix.tail

import cats.Eq
import cats.effect.Effect
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.instances.all._
import cats.laws.IsEq
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import monix.eval.Task
import monix.eval.instances.CatsEffectForTask
import monix.execution.Scheduler
import monix.tail.Iterant
import org.scalacheck.{Arbitrary, Gen}
import otters.FunctionPipe
import otters.laws.AsyncStreamLaws
import otters.laws.discipline.{AsyncStreamTests, TestBase}

import scala.concurrent.Future

class IterantPipeInstancesTests extends TestBase with TestInstances {
  type TestPipe[A] = FunctionPipe[Iterant[Task, ?], Int, A]

  implicit val taskEffect: Effect[Task] = new CatsEffectForTask()(Scheduler(TestContext()))

  implicit val asyncIterantLaws: AsyncStreamLaws[TestPipe, Task] = AsyncStreamLaws[TestPipe, Task]

  implicit def pipeArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[TestPipe[A]] =
    Arbitrary(ev.arbitrary.map(as => (_: Iterant[Task, Int]) => Iterant.fromList(as)))

  implicit def streamEq[A](implicit ev: Eq[Future[List[A]]], ec: TestContext): Eq[TestPipe[A]] = {
    implicit val sched: Scheduler = Scheduler(ec)

    Eq.by(_(Iterant.pure(1)).toListL.runAsync.asInstanceOf[Future[List[A]]])
  }

  implicit def pureTask[A]: Arbitrary[A => Task[A]] = Arbitrary(Gen.const((a: A) => Task(a)))

  implicit def iso(implicit ev: Isomorphisms[Iterant[Task, ?]]): Isomorphisms[TestPipe] = new Isomorphisms[TestPipe] {
    override def associativity[A, B, C](
      fs: (TestPipe[(A, (B, C))], TestPipe[((A, B), C)])
    ): IsEq[TestPipe[(A, B, C)]] = {
      val (l, r) = fs
      val stream = Iterant.pure[Task, Int](1)
      val iseq = ev.associativity((l(stream), r(stream)))
      IsEq((_: Iterant[Task, Int]) => iseq.lhs, (_: Iterant[Task, Int]) => iseq.rhs)
    }

    override def leftIdentity[A](fs: (TestPipe[(Unit, A)], TestPipe[A])): IsEq[TestPipe[A]] = {
      val (l, r) = fs
      val stream = Iterant.pure[Task, Int](1)
      val iseq = ev.leftIdentity((l(stream), r(stream)))
      IsEq((_: Iterant[Task, Int]) => iseq.lhs, (_: Iterant[Task, Int]) => iseq.rhs)
    }

    override def rightIdentity[A](fs: (TestPipe[(A, Unit)], TestPipe[A])): IsEq[TestPipe[A]] = {
      val (l, r) = fs
      val stream = Iterant.pure[Task, Int](1)
      val iseq = ev.rightIdentity((l(stream), r(stream)))
      IsEq((_: Iterant[Task, Int]) => iseq.lhs, (_: Iterant[Task, Int]) => iseq.rhs)
    }
  }

  checkAllAsync(
    "Iterant[Task, Int] => Iterant[Task, Int]",
    implicit ec => AsyncStreamTests[TestPipe, Task].asyncStream[Int, Int, Int]
  )
}
