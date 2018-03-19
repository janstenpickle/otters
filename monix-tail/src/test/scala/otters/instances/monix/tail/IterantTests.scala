package otters.instances.monix.tail

import _root_.monix.eval.Task
import _root_.monix.eval.instances.CatsEffectForTask
import _root_.monix.execution.Scheduler
import _root_.monix.tail.Iterant
import cats.Eq
import cats.effect.Effect
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.instances.all._
import cats.laws.discipline.arbitrary._
import org.scalacheck.{Arbitrary, Gen}
import otters.laws.AsyncStreamLaws
import otters.laws.discipline.{AsyncStreamTests, TestBase}
import otters.{FunctionPipe, FunctionSink}

import scala.concurrent.Future

class IterantTests extends TestBase with TestInstances {
  implicit val taskEffect: Effect[Task] = new CatsEffectForTask()(Scheduler(TestContext()))

  implicit val asyncStreamLaws: AsyncStreamLaws[Iterant[Task, ?], Task] = AsyncStreamLaws[Iterant[Task, ?], Task]

  implicit def iterantArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[Iterant[Task, A]] =
    Arbitrary(ev.arbitrary.map(as => Iterant.fromList(as)))

  implicit def iterantEq[A](implicit ev: Eq[Future[List[A]]], ec: TestContext): Eq[Iterant[Task, A]] = {
    implicit val sched: Scheduler = Scheduler(ec)

    Eq.by(_.toListL.runAsync.asInstanceOf[Future[List[A]]])
  }

  implicit def taskEq[A: Eq](implicit ec: TestContext): Eq[Task[A]] = {
    implicit val sched: Scheduler = Scheduler(ec)

    Eq.by(_.runAsync.asInstanceOf[Future[A]])
  }

  implicit def pureTask[A]: Arbitrary[A => Task[A]] = Arbitrary(Gen.const(Task.now[A]))

  checkAllAsync(
    "Iterant[Task, Int]",
    implicit ec => AsyncStreamTests[Iterant[Task, ?], Task].asyncStream[Int, Int, Int]
  )
}
