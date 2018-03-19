package otters.instances.fs2

import _root_.fs2.Stream
import cats.Eq
import cats.effect.IO
import cats.effect.laws.util.{TestContext, TestInstances}
import cats.instances.all._
import org.scalacheck.{Arbitrary, Gen}
import otters.laws.AsyncStreamLaws
import otters.laws.discipline.{AsyncStreamTests, TestBase}
import otters.{FunctionPipe, FunctionSink}

class Fs2InstancesTests extends TestBase with TestInstances {
  implicit val asyncStreamLaws: AsyncStreamLaws[Stream[IO, ?], IO] = AsyncStreamLaws[Stream[IO, ?], IO]

  implicit def streamArb[A](implicit ev: Arbitrary[List[A]]): Arbitrary[Stream[IO, A]] =
    Arbitrary(ev.arbitrary.map(as => Stream(as: _*)))

  implicit def streamEq[A](implicit ev: Eq[IO[List[A]]], ec: TestContext): Eq[Stream[IO, A]] =
    Eq.by(_.compile.toList)

  implicit def pureIO[A]: Arbitrary[A => IO[A]] = Arbitrary(Gen.const((a: A) => IO(a)))

  checkAllAsync("Stream[IO, Int]", implicit ec => AsyncStreamTests[Stream[IO, ?], IO].asyncStream[Int, Int, Int])
}
