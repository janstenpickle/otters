package otters

import cats.data.{NonEmptyList, WriterT}
import cats.instances.all._
import cats.kernel.Monoid
import cats.syntax.applicative._
import cats.syntax.flatMap._
import cats.syntax.functor._
import otters.syntax.{WriterTExtendedSyntax, WriterTSyntax}

import scala.concurrent.duration._

trait WriterStreamSuite[F[_], G[_], H[_], P[_, _], S[_, _]] extends TestBase[F, G, H, P, S] {
  object WriterSyntax extends WriterTSyntax with WriterTExtendedSyntax[P, S]
  import WriterSyntax._

  def mkWriterStream[L: Monoid, A](src: F[A]): WriterT[F, L, A]
  def mkWriterStream[L, A](src: F[A], initial: L): WriterT[F, L, A]
  def mkWriterStream[L, A](src: F[(L, A)]): WriterT[F, L, A]

  def runConcat[S: Monoid, A, B](source: F[(S, A)])(f: WriterT[F, S, A] => WriterT[F, S, B]): Seq[(S, B)] =
    runStream(f(mkWriterStream[S, A](source)).run)

  test("can make a stream with empty state") {
    forAll { values: NonEmptyList[String] =>
      val ret = runStream(mkWriterStream[Int, String](F.fromSeq(values.toList)).run)

      assert(ret === values.map(0 -> _).toList)
    }
  }

  test("can make a stream with initial state") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val ret = runStream(mkWriterStream[Int, String](F.fromSeq(values.toList), state).run)

      assert(ret === values.map(state -> _).toList)
    }
  }

  test("can map over state") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(mkWriterStream[Int, String](F.pure(state -> value)).map(_.length).run)

      val (st, fin) = ret.head

      assert(ret.size === 1 && st === state && fin === value.length)
    }
  }

  test("can map state and data") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret =
        runStream(mkWriterStream[Int, String](F.fromSeq(values.toList)).mapBoth((i, s) => (i.toString, s.length)).run)

      assert(ret.map(_._1) === values.map(_._1.toString).toList && ret.map(_._2) === values.map(_._2.length).toList)
    }
  }

  test("can mapAsync state and data with parallelism") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .mapBothAsync(parallelism)((i, s) => (i.toString -> s.length).pure[G])
          .run
      )

      assert(ret.map(_._1) === values.map(_._1.toString).toList && ret.map(_._2) === values.map(_._2.length).toList)
    }
  }

  test("can mapAsync state and data") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .mapBothAsync((i, s) => (i.toString -> s.length).pure[G])
          .run
      )

      assert(ret.map(_._1) === values.map(_._1.toString).toList && ret.map(_._2) === values.map(_._2.length).toList)
    }
  }

  test("can bimapAsync state and data with parallelism") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .bimapAsync(parallelism)(_.toString.pure[G], _.length.pure[G])
          .run
      )

      assert(ret.map(_._1) === values.map(_._1.toString).toList && ret.map(_._2) === values.map(_._2.length).toList)
    }
  }

  test("can bimapAsync state and data") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .bimapAsync(_.toString.pure[G], _.length.pure[G])
          .run
      )

      assert(ret.map(_._1) === values.map(_._1.toString).toList && ret.map(_._2) === values.map(_._2.length).toList)
    }
  }

  test("can swap state and data") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret = runStream(mkWriterStream[Int, String](F.fromSeq(values.toList)).swap.run)

      assert(ret === values.map(x => (x._2, x._1)).toList)
    }
  }

  test("can bimap state and data") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret = runStream(mkWriterStream[Int, String](F.fromSeq(values.toList)).bimap(_.toString, _.length).run)

      assert(ret.map(_._1) === values.map(_._1.toString).toList && ret.map(_._2) === values.map(_._2.length).toList)
    }
  }

  test("can mapAsync over state with parallelism") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret =
        runStream(
          mkWriterStream[Int, String](F.fromSeq(values.toList)).mapAsync(parallelism)(v => G.pure(v.length)).run
        )

      assert(ret.size === values.size && ret.toList === values.map {
        case (s, v) => (s, v.length)
      }.toList)
    }
  }

  test("can mapAsync over state") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret =
        runStream(mkWriterStream[Int, String](F.fromSeq(values.toList)).mapAsync(v => G.pure(v.length)).run)

      assert(ret.size === values.size && ret.toList === values.map {
        case (s, v) => (s, v.length)
      }.toList)
    }
  }

  test("can flatMapAsync writer with parallelism") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .flatMapAsync(parallelism)(v => WriterT(G.pure(v.length -> v)))
          .run
      )

      assert(ret === values.map { case (i, s) => (i + s.length, s) }.toList)
    }
  }

  test("can flatMapAsync writer") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val ret = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .flatMapAsync(v => WriterT(G.pure(v.length -> v)))
          .run
      )

      assert(ret === values.map { case (i, s) => (i + s.length, s) }.toList)
    }
  }

  test("can modify state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(mkWriterStream[Int, String](F.pure(state -> value)).mapWritten(_.toString).run)

      val (st, fin) = ret.head

      assert(ret.size === 1 && st === state.toString && fin === value)
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
        runConcat(F.fromSeq(repeat(state -> value)(20)))(_.grouped(10))

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
      val data = runStream(mkWriterStream[Int, String](F.pure(state -> value)).run)

      assert(data === Seq(state -> value))
    }
  }

  test("can send state and data by independent flows") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val data = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .via(mkPipe(_.toString), mkPipe(_.length))
          .run
      )

      assert(data === values.map { case (s, d) => s.toString -> d.length }.toList)
    }
  }

  test("can send state by separate flows") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val data = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .stateVia(mkPipe(_.toString))
          .run
      )

      assert(data === values.map { case (s, d) => s.toString -> d }.toList)
    }
  }

  test("can send data by separate flow") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val data = runStream(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .dataVia(mkPipe(_.length))
          .run
      )

      assert(data === values.map { case (s, d) => s -> d.length }.toList)
    }
  }

  test("can send state and data to separate sinks") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val (state, data) = materialize(
        mkWriterStream[Int, String](F.fromSeq(values.toList))
          .toSinksTupled(mkSeqSink, mkSeqSink)
      )

      assert(waitFor(state) === values.map(_._1).toList && waitFor(data) === values.map(_._2).toList)
    }
  }

  test("can materialize state and data with separate sinks, combining the results") {
    forAll { values: NonEmptyList[(Int, String)] =>
      val (stateRes, valueRes) = waitFor(
        materialize(
          mkWriterStream[Int, String](F.fromSeq(values.toList))
            .toSinks(mkSeqSink[Int], mkSeqSink[String])((l, r) => l.flatMap(ll => r.map((ll, _))))
        )
      )

      assert(stateRes === values.map(_._1).toList && valueRes === values.map(_._2).toList)
    }
  }

}
