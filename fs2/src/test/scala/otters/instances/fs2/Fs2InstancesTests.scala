package otters.instances.fs2

import cats.effect.IO
import cats.effect.laws.util.{TestContext, TestInstances}
import otters.laws.{AsyncStreamLaws, TupleStreamLaws}
import otters.laws.discipline.{AsyncStreamTests, TestBase, TupleStreamTests}
import _root_.fs2.Stream
import cats.Eq
import cats.instances.all._
import org.scalacheck.{Arbitrary, Gen}
import otters.{Pipe, Sink}

class Fs2InstancesTests extends TestBase with TestInstances {
  implicit val streamLaws: TupleStreamLaws[Stream[IO, ?], IO, IO] = TupleStreamLaws[Stream[IO, ?], IO, IO]
  implicit val asyncStreamLaws: AsyncStreamLaws[Stream[IO, ?], IO] = AsyncStreamLaws[Stream[IO, ?], IO]

  implicit def streamArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[Stream[IO, A]] =
    Arbitrary(ev.arbitrary.map(as => Stream(as: _*)))

  implicit def pipeArb[A, B](implicit ev: Arbitrary[A => B]): Arbitrary[Pipe[Stream[IO, ?], A, B]] =
    Arbitrary(ev.arbitrary.map(f => (s: Stream[IO, A]) => s.map(f)))

  implicit def sinkFnArb[A, B](
    implicit ev: Arbitrary[A => B],
    ec: TestContext
  ): Arbitrary[Sink[Stream[IO, ?], IO, A, IO[List[B]]]] =
    Arbitrary(
      ev.arbitrary
        .map(f => (s: Stream[IO, A]) => IO(s.map(f).compile.toList))
    )

  implicit def sinkArb[A](
    implicit
    ec: TestContext
  ): Arbitrary[Sink[Stream[IO, ?], IO, A, IO[List[A]]]] =
    Arbitrary(Gen.const((s: Stream[IO, A]) => IO(s.compile.toList)))

  implicit def streamEq[A](implicit ev: Eq[IO[List[A]]], ec: TestContext): Eq[Stream[IO, A]] =
    Eq.by(_.compile.toList)

  implicit def pureIO[A]: Arbitrary[A => IO[A]] = Arbitrary(Gen.const((a: A) => IO(a)))

  checkAllAsync("Stream[IO, Int]", implicit ec => TupleStreamTests[Stream[IO, ?], IO, IO].tupleStream[Int, Int, Int])
  checkAllAsync("Stream[IO, Int]", implicit ec => AsyncStreamTests[Stream[IO, ?], IO].asyncStream[Int, Int])
}
