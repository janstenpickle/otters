package otters.instances.fs2

import _root_.fs2.Stream
import cats.Eq
import cats.effect.internals.IOContextShift
import cats.effect.{ContextShift, IO}
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.instances.all._
import cats.laws.IsEq
import cats.laws.discipline.SemigroupalTests.Isomorphisms
import org.scalacheck.{Arbitrary, Gen}
import otters.FunctionPipe
import otters.laws.AsyncStreamLaws
import otters.laws.discipline.{AsyncStreamTests, TestBase}

class Fs2PipeInstancesTests extends TestBase with TestInstances {
  implicit val cs: ContextShift[IO] = IOContextShift.global

  type TestPipe[A] = FunctionPipe[Stream[IO, ?], Int, A]
  implicit val asyncStreamLaws: AsyncStreamLaws[TestPipe, IO] = AsyncStreamLaws[TestPipe, IO]

  implicit def pipeArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[TestPipe[A]] =
    Arbitrary(ev.arbitrary.map(as => (_: Stream[IO, Int]) => Stream(as: _*)))

  implicit def streamEq[A](implicit ev: Eq[IO[List[A]]], ec: TestContext): Eq[TestPipe[A]] =
    Eq.by(_(Stream.emit(1)).compile.toList)

  implicit def pureIO[A]: Arbitrary[A => IO[A]] = Arbitrary(Gen.const((a: A) => IO(a)))

  implicit def iso(implicit ev: Isomorphisms[Stream[IO, ?]]): Isomorphisms[TestPipe] = new Isomorphisms[TestPipe] {
    override def associativity[A, B, C](
      fs: (TestPipe[(A, (B, C))], TestPipe[((A, B), C)])
    ): IsEq[TestPipe[(A, B, C)]] = {
      val (l, r) = fs
      val stream = Stream.emit(1)
      val iseq = ev.associativity((l(stream), r(stream)))
      IsEq((_: Stream[IO, Int]) => iseq.lhs, (_: Stream[IO, Int]) => iseq.rhs)
    }

    override def leftIdentity[A](fs: (TestPipe[(Unit, A)], TestPipe[A])): IsEq[TestPipe[A]] = {
      val (l, r) = fs
      val stream = Stream.emit(1)
      val iseq = ev.leftIdentity((l(stream), r(stream)))
      IsEq((_: Stream[IO, Int]) => iseq.lhs, (_: Stream[IO, Int]) => iseq.rhs)
    }

    override def rightIdentity[A](fs: (TestPipe[(A, Unit)], TestPipe[A])): IsEq[TestPipe[A]] = {
      val (l, r) = fs
      val stream = Stream.emit(1)
      val iseq = ev.rightIdentity((l(stream), r(stream)))
      IsEq((_: Stream[IO, Int]) => iseq.lhs, (_: Stream[IO, Int]) => iseq.rhs)
    }
  }

  checkAllAsync(
    "Stream[IO, Int] => Stream[IO, Int]",
    implicit ec => AsyncStreamTests[TestPipe, IO].asyncStream[Int, Int, Int]
  )
}
