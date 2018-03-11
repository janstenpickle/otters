package statestream

import cats.data.{NonEmptyList, StateT, WriterT}
import cats.instances.all._
import cats.kernel.Monoid
import cats.syntax.flatMap._
import cats.syntax.functor._
import cats.syntax.applicative._
import cats.{~>, Monad}
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.FunSuite
import org.scalatest.prop.PropertyChecks

import scala.concurrent.duration._

trait WriterStreamSuite[F[_], G[_], H[_], I[_]] extends FunSuite with PropertyChecks {
  import WriterStreamSuite._

  implicit def F: TupleStream[F, H, I]
  implicit def G: Monad[G]
  implicit def H: Monad[H]
  implicit def nat: G ~> H

  def mkWriterStream[S: Monoid, A](src: F[A]): WriterStream[F, G, H, I, S, A]
  def mkWriterStream[S, A](src: F[(S, A)]): WriterStream[F, G, H, I, S, A]
  def mkPipe[A, B](f: A => B): Pipe[F, A, B]
  def mkSeqSink[A]: Sink[F, I, A, H[Seq[A]]]

  def extract[A](fa: G[A]): A

  def runConcat[S: Monoid, A, B](
    source: F[(S, A)]
  )(f: WriterStream[F, G, H, I, S, A] => WriterStream[F, G, H, I, S, B]): Seq[(S, B)] =
    runStream(f(mkWriterStream[S, A](source)).stream).map(v => extract(v.run))

  def runStream[A](stream: F[A]): Seq[A]

  def materialize[A](i: I[A]): A

  def waitFor[A](fut: H[A]): A

  test("can map over state") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(mkWriterStream[Int, String](F.pure(state -> value)).map(_.length).stream)

      val (st, fin) = extract(ret.head.run)

      assert(ret.size === 1 && st === state && fin === value.length)
    }
  }

  test("can mapAsync over state") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret =
        runStream(
          mkWriterStream[Int, String](F.fromSeq(values.toList)).mapAsync(parallelism)(v => H.pure(v.length)).stream
        )

      assert(ret.size === values.size && ret.map(_.run).map(extract).toList === values.map {
        case (s, v) => (s, v.length)
      }.toList)
    }
  }

  test("can flatMap state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(
          mkWriterStream[Int, String](F.pure(state -> value)).flatMapWriter(v => WriterT((v.length, v).pure[G])).stream
        )

      val (st, fin) = extract(ret.head.run)

      assert(ret.size === 1 && st === state + value.length && fin === value)
    }
  }

  test("can flatMapAsync state") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(
        mkWriterStream[Int, String](F.pure(state -> value))
          .flatMapWriterAsync(parallelism)(v => WriterT(H.pure(v.length -> v)))
          .stream
      )

      val (st, fin) = extract(ret.head.run)

      assert(ret.size === 1 && st === state + value.length && fin === value)
    }
  }

  test("can modify state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(mkWriterStream[Int, String](F.pure(state -> value)).mapWritten(_.toString).stream)

      val (st, fin) = extract(ret.head.run)

      assert(ret.size === 1 && st === state.toString && fin === value)
    }
  }

  test("can concatenate collection, putting state ") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(mkWriterStream[Int, String](F.pure(state -> value)).mapBoth(_.toString -> _.length).stream)

      val (st, fin) = extract(ret.head.run)

      assert(ret.size === 1 && st === state.toString && fin === value.length)
    }
  }

  test("puts state on to the head of a stream when performing mapConcatHead") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(F.pure(state -> values))(_.mapConcatHead(_.toList))
      val (headState, _) = data.head
      val tailState = data.tail.map(_._1).sum
      val returnValues = data.map(_._2)

      assert(headState === state && tailState === 0 && returnValues === values.toList)
    }
  }

  test("puts state on to the end of a stream when performing mapConcatTail") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(F.pure(state -> values))(_.mapConcatTail(_.toList))
      val (endState, _) = data.last
      val (front :+ _) = data.map(_._1)

      assert(endState === state && front.sum === 0 && data.map(_._2) === values.toList)
    }
  }

  test("copies state to each element of a stream when performing mapConcatAll") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(F.pure(state -> values))(_.mapConcatAll(_.toList))

      assert(
        data.map(_._1).sum === repeat(state)(values.size).sum && data
          .map(_._2) === values.toList
      )
    }
  }

  test("puts state on to the head of a stream when performing safeMapConcatHead") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(F.pure(state -> values))(_.safeMapConcatHead(_.toList.toSet))
      val (headState, _) = data.head
      val tailState = data.tail.map(_._1).sum
      val returnValues = data.map(_._2)

      assert(headState === state && tailState === 0 && returnValues.toSet === values.map(Some(_)).toList.toSet)
    }
  }

  test("puts state on to the end of a stream when performing safeMapConcatTail") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(F.pure(state -> values))(_.safeMapConcatTail(_.toList.toSet))
      val (endState, _) = data.last
      val (front :+ _) = data.map(_._1)

      assert(endState === state && front.sum === 0 && data.map(_._2).toSet === values.map(Some(_)).toList.toSet)
    }
  }

  test("copies state to each element of a stream when performing safeMapConcatAll") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(F.pure(state -> values))(_.safeMapConcatAll(_.toList.toSet))

      assert(
        data.map(_._1).sum === repeat(state)(values.toList.toSet.size).sum && data
          .map(_._2)
          .toSet === values.map(Some(_)).toList.toSet
      )
    }
  }

  test("erases state when performing mapConcatHead with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(F.pure(state -> List.empty[String]))(_.mapConcatHead(identity))

      assert(data.isEmpty)
    }
  }

  test("erases state when performing mapConcatTail with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(F.pure(state -> List.empty[String]))(_.mapConcatTail(identity))

      assert(data.isEmpty)
    }
  }

  test("erases state when performing mapConcatAll with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(F.pure(state -> List.empty[String]))(_.mapConcatAll(identity))

      assert(data.isEmpty)
    }
  }

  test("preserves state when performing safeMapConcatHead with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(F.pure(state -> List.empty[String]))(_.safeMapConcatHead(identity))

      assert(data.map(_._1).sum === state)
    }
  }

  test("preserves state when performing safeMapConcatTail with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(F.pure(state -> List.empty[String]))(_.safeMapConcatTail(identity))

      assert(data.map(_._1).sum === state)
    }
  }

  test("preserves state when performing safeMapConcatAll with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(F.pure(state -> List.empty[String]))(_.safeMapConcatAll(identity))

      assert(data.map(_._1).sum === state)
    }
  }

  test("groups data and aggregates state") {
    forAll { (state: Int, value: String) =>
      val data =
        runConcat(F.fromSeq(repeat(state -> value)(20)))(_.group(10))

      assert(data.map(_._1) === repeat(state * 10)(2) && data.map(_._2) === repeat(repeat(value)(10))(2))
    }
  }

  test("groups data within a time range and aggregates state") {
    forAll { (state: Int, value: String) =>
      val data =
        runConcat(F.fromSeq(repeat(state -> value)(20)))(_.groupedWithin(10, 1.second))

      assert(data.map(_._1) === repeat(state * 10)(2) && data.map(_._2) === repeat(repeat(value)(10))(2))
    }
  }

  test("converts state and data to a tuple") {
    forAll { (state: Int, value: String) =>
      val data = runStream(mkWriterStream[Int, String](F.pure(state -> value)).toTuple)

      assert(data === Seq(state -> value))
    }
  }

  test("can send state and data by independent flows") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val data = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .via(mkPipe(_.toString), mkPipe(_.length))
          .stream
      )

      assert(data.map(v => extract(v.run)) === values.map { case (s, d) => s.toString -> d.length }.toList)
    }
  }

  test("can send state by separate flows") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val data = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .stateVia(mkPipe(_.toString))
          .stream
      )

      assert(data.map(v => extract(v.run)) === values.map { case (s, d) => s.toString -> d }.toList)
    }
  }

  test("can send data by separate flow") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val data = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .dataVia(mkPipe(_.length))
          .stream
      )

      assert(data.map(v => extract(v.run)) === values.map { case (s, d) => s -> d.length }.toList)
    }
  }

  test("can send state and data to separate sinks") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val (state, data) = materialize(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .toSinks(mkSeqSink, mkSeqSink)
      )

      assert(waitFor(state) === values.map(_._1).toList && waitFor(data) === values.map(_._2).toList)
    }
  }

  test("can materialize state and data with separate sinks, combining the results") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val (stateRes, valueRes) = waitFor(
        materialize(
          mkWriterStream[Int, String](F.fromSeq(values.toList))
            .toSinks[H[Seq[Int]], H[Seq[String]], H[(Seq[Int], Seq[String])]](mkSeqSink[Int], mkSeqSink[String])(
              (l, r) => l.flatMap(ll => r.map((ll, _)))
            )
        )
      )

      assert(stateRes === values.map(_._1).toList && valueRes === values.map(_._2).toList)
    }
  }

}

object WriterStreamSuite {
  val parallelism: Int = 3

  implicit def nelArb[A](implicit arb: Arbitrary[A]): Arbitrary[NonEmptyList[A]] =
    Arbitrary(for {
      head <- arb.arbitrary
      tail <- Gen.listOf(arb.arbitrary)
    } yield NonEmptyList.of(head, tail: _*))

  def repeat[A](v: A)(n: Int): List[A] = scala.Stream.continually(v).take(n).toList

}
