package otters

import cats.Monad
import cats.data.NonEmptyList
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FunSuite
import org.scalatest.prop.PropertyChecks
import otters.syntax.WriterTSyntax

trait TestBase[F[_], G[_], H[_], P[_, _], S[_, _]] extends FunSuite with PropertyChecks {
  implicit def F: EitherStream[F, G, H, P, S]
  implicit def G: Monad[G]

  def mkPipe[A, B](f: A => B): P[A, B]
  def mkSeqSink[A]: S[A, G[Seq[A]]]

  def runStream[A](stream: F[A]): Seq[A]

  def materialize[A](i: H[A]): A

  def waitFor[A](fut: G[A]): A

  val parallelism: Int = 3

  implicit def nelArb[A](implicit arb: Arbitrary[A]): Arbitrary[NonEmptyList[A]] =
    Arbitrary(for {
      head <- arb.arbitrary
      tail <- Gen.listOf(arb.arbitrary)
    } yield NonEmptyList.of(head, tail: _*))

  def repeat[A](v: A)(n: Int): List[A] = scala.Stream.continually(v).take(n).toList
}
