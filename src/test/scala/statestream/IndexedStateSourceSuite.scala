package statestream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import cats.{~>, Monad}
import cats.data.{NonEmptyList, StateT}
import cats.instances.all._
import cats.kernel.Monoid
import org.scalacheck.{Arbitrary, Gen}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

trait IndexedStateSourceSuite[F[_]] extends FunSuite with PropertyChecks with BeforeAndAfterAll {
  import IndexedStateSourceSuite._

  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ex: ExecutionContext = as.dispatcher

  implicit def F: Monad[F]
  implicit def nat: F ~> Future

  def mkStateSource[S, A](src: Source[A, NotUsed]): IndexedStateSource[F, S, S, A]
  def mkStateSource[S: Monoid, A](src: Source[(S, A), NotUsed]): IndexedStateSource[F, S, S, A]

  def extract[A](fa: F[A]): A

  def runConcat[S: Monoid, A, B](
    source: Source[(S, A), NotUsed]
  )(f: IndexedStateSource[F, S, S, A] => IndexedStateSource[F, S, S, B])(implicit mat: ActorMaterializer): Seq[(S, B)] =
    runStream(f(mkStateSource[S, A](source)).stream).map(v => extract(v.runEmpty))

  test("can map over state") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(mkStateSource[Int, String](Source.single(value)).map(_.length).stream)

      val (st, fin) = extract(ret.head.run(state))

      assert(ret.size === 1 && st === state && fin === value.length)
    }
  }

  test("can mapAsync over state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(mkStateSource[Int, String](Source.single(value)).mapAsync(parallelism)(v => Future(v.length)).stream)

      val (st, fin) = extract(ret.head.run(state))

      assert(ret.size === 1 && st === state && fin === value.length)
    }
  }

  test("can flatMap state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(
          mkStateSource[Int, String](Source.single(value)).flatMap(v => StateT(s => F.pure((v.length + s, v)))).stream
        )

      val (st, fin) = extract(ret.head.run(state))

      assert(ret.size === 1 && st === state + value.length && fin === value)
    }
  }

  test("can flatMapAsync state") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(
        mkStateSource[Int, String](Source.single(value))
          .flatMapAsync(parallelism)(v => StateT(s => Future(v.length + s, v)))
          .stream
      )

      val (st, fin) = extract(ret.head.run(state))

      assert(ret.size === 1 && st === state + value.length && fin === value)
    }
  }

  test("can modify state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(mkStateSource[Int, String](Source.single(value)).modify(_.toString).stream)

      val (st, fin) = extract(ret.head.run(state))

      assert(ret.size === 1 && st === state.toString && fin === value)
    }
  }

  test("can concatenate collection, putting state ") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(mkStateSource[Int, String](Source.single(value)).transform(_.toString -> _.length).stream)

      val (st, fin) = extract(ret.head.run(state))

      assert(ret.size === 1 && st === state.toString && fin === value.length)
    }
  }

  test("puts state on to the head of a stream when performing mapConcatHead") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(Source.single(state -> values))(_.mapConcatHead(_.toList))
      val (headState, _) = data.head
      val tailState = data.tail.map(_._1).sum
      val returnValues = data.map(_._2)

      assert(headState === state && tailState === 0 && returnValues === values.toList)
    }
  }

  test("puts state on to the end of a stream when performing mapConcatTail") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(Source.single(state -> values))(_.mapConcatTail(_.toList))
      val (endState, _) = data.last
      val (front :+ _) = data.map(_._1)

      assert(endState === state && front.sum === 0 && data.map(_._2) === values.toList)
    }
  }

  test("copies state to each element of a stream when performing mapConcatAll") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(Source.single(state -> values))(_.mapConcatAll(_.toList))

      assert(
        data.map(_._1).sum === repeat(state)(values.size).sum && data
          .map(_._2) === values.toList
      )
    }
  }

  test("puts state on to the head of a stream when performing safeMapConcatHead") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(Source.single(state -> values))(_.safeMapConcatHead(_.toList.toSet))
      val (headState, _) = data.head
      val tailState = data.tail.map(_._1).sum
      val returnValues = data.map(_._2)

      assert(headState === state && tailState === 0 && returnValues.toSet === values.map(Some(_)).toList.toSet)
    }
  }

  test("puts state on to the end of a stream when performing safeMapConcatTail") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(Source.single(state -> values))(_.safeMapConcatTail(_.toList.toSet))
      val (endState, _) = data.last
      val (front :+ _) = data.map(_._1)

      assert(endState === state && front.sum === 0 && data.map(_._2).toSet === values.map(Some(_)).toList.toSet)
    }
  }

  test("copies state to each element of a stream when performing safeMapConcatAll") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(Source.single(state -> values))(_.safeMapConcatAll(_.toList.toSet))

      assert(
        data.map(_._1).sum === Stream.continually(state).take(values.toList.toSet.size).sum && data
          .map(_._2)
          .toSet === values.map(Some(_)).toList.toSet
      )
    }
  }

  test("erases state when performing mapConcatHead with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(Source.single(state -> List.empty[String]))(_.mapConcatHead(identity))

      assert(data.isEmpty)
    }
  }

  test("erases state when performing mapConcatTail with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(Source.single(state -> List.empty[String]))(_.mapConcatTail(identity))

      assert(data.isEmpty)
    }
  }

  test("erases state when performing mapConcatAll with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(Source.single(state -> List.empty[String]))(_.mapConcatAll(identity))

      assert(data.isEmpty)
    }
  }

  test("preserves state when performing safeMapConcatHead with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(Source.single(state -> List.empty[String]))(_.safeMapConcatHead(identity))

      assert(data.map(_._1).sum === state)
    }
  }

  test("preserves state when performing safeMapConcatTail with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(Source.single(state -> List.empty[String]))(_.safeMapConcatTail(identity))

      assert(data.map(_._1).sum === state)
    }
  }

  test("preserves state when performing safeMapConcatAll with an empty collection") {
    forAll { (state: Int) =>
      val data = runConcat(Source.single(state -> List.empty[String]))(_.safeMapConcatAll(identity))

      assert(data.map(_._1).sum === state)
    }
  }

  test("groups data and aggregates state") {
    forAll { (state: Int, value: String) =>
      val data =
        runConcat(Source.fromIterator(() => repeat(state -> value)(20).iterator))(_.group(10))

      assert(data.map(_._1) === repeat(state * 10)(2) && data.map(_._2) === repeat(repeat(value)(10))(2))
    }
  }

  test("groups data within a time range and aggregates state") {
    forAll { (state: Int, value: String) =>
      val data =
        runConcat(Source.fromIterator(() => repeat(state -> value)(20).iterator))(_.groupedWithin(10, 1.second))

      assert(data.map(_._1) === repeat(state * 10)(2) && data.map(_._2) === repeat(repeat(value)(10))(2))
    }
  }

  test("converts state and data to a tuple") {
    forAll { (state: Int, value: String) =>
      val data = runStream(mkStateSource(Source.single(state -> value)).toTuple)

      assert(data === Seq(state -> value))
    }
  }

  test("converts state and data to a tuple when provided initial state") {
    forAll { (state: Int, value: String) =>
      val data = runStream(mkStateSource[Int, String](Source.single(value)).toTuple(state))

      assert(data === Seq(state -> value))
    }
  }

  test("can send state and data by independent flows") {
    forAll { (state: Int, value: String) =>
      val data = runStream(
        mkStateSource[Int, String](Source.single(state -> value))
          .via(Flow.fromFunction(_.toString), Flow.fromFunction(_.length))
          .stream
      )

      assert(data.map(v => extract(v.runEmpty)) === Seq(state.toString -> value.length))
    }
  }

  test("can send state by separate flows") {
    forAll { (state: Int, value: String) =>
      val data = runStream(
        mkStateSource[Int, String](Source.single(state -> value))
          .stateVia(Flow.fromFunction(_.toString))
          .stream
      )

      assert(data.map(v => extract(v.runEmpty)) === Seq(state.toString -> value))
    }
  }

  test("can send data by separate flow") {
    forAll { (state: Int, value: String) =>
      val data = runStream(
        mkStateSource[Int, String](Source.single(state -> value))
          .dataVia(Flow.fromFunction(_.length))
          .stream
      )

      assert(data.map(v => extract(v.runEmpty)) === Seq(state -> value.length))
    }
  }

  test("can send state and data to separate sinks") {
    forAll { (state: Int, value: String) =>
      val stateSink = new TestSink[Int]
      val dataSink = new TestSink[String]

      mkStateSource[Int, String](Source.single(state -> value)).to(stateSink.sink, dataSink.sink).run()

      Thread.sleep(100)

      assert(stateSink.data === Seq(state) && dataSink.data === Seq(value))
    }
  }

  test("can send state and data to separate sinks when provided with an initial state") {
    forAll { (state: Int, value: String) =>
      val stateSink = new TestSink[Int]
      val dataSink = new TestSink[String]

      mkStateSource[Int, String](Source.single(value)).to(state)(stateSink.sink, dataSink.sink).run()

      Thread.sleep(100)

      assert(stateSink.data === Seq(state) && dataSink.data === Seq(value))
    }
  }

  test("can materialize state and data with separate sinks") {
    forAll { (state: Int, value: String) =>
      val (stateRes, valueRes) = mkStateSource[Int, String](Source.single(state -> value))
        .runWith[Future[Seq[Int]], Future[Seq[String]]](Sink.seq[Int], Sink.seq[String])

      assert(waitFor(stateRes) === Seq(state) && waitFor(valueRes) === Seq(value))
    }
  }

  test("can materialize state and data with separate sinks when provided with an initial state") {
    forAll { (state: Int, value: String) =>
      val (stateRes, valueRes) = mkStateSource[Int, String](Source.single(value))
        .runWith[Future[Seq[Int]], Future[Seq[String]]](state)(Sink.seq[Int], Sink.seq[String])

      assert(waitFor(stateRes) === Seq(state) && waitFor(valueRes) === Seq(value))
    }
  }

  test("can materialize state and data with separate sinks, combining the results") {
    forAll { (state: Int, value: String) =>
      val (stateRes, valueRes) = waitFor(
        mkStateSource[Int, String](Source.single(state -> value))
          .runWith[Future[Seq[Int]], Future[Seq[String]], Future[(Seq[Int], Seq[String])]](
            Sink.seq[Int],
            Sink.seq[String]
          )((l, r) => l.flatMap(ll => r.map((ll, _))))
      )

      assert(stateRes === Seq(state) && valueRes === Seq(value))
    }
  }

  test("can materialize state and data with separate sinks and initial state, combining the results") {
    forAll { (state: Int, value: String) =>
      val (stateRes, valueRes) = waitFor(
        mkStateSource[Int, String](Source.single(value))
          .runWith[Future[Seq[Int]], Future[Seq[String]], Future[(Seq[Int], Seq[String])]](state)(
            Sink.seq[Int],
            Sink.seq[String]
          )((l, r) => l.flatMap(ll => r.map((ll, _))))
      )

      assert(stateRes === Seq(state) && valueRes === Seq(value))
    }
  }

  test("can send state to a sink returning a data stream") {
    forAll { (state: Int, value: String) =>
      val stateSink = new TestSink[Int]

      val data = runStream(mkStateSource[Int, String](Source.single(state -> value)).passThroughData(stateSink.sink))

      assert(stateSink.data === Seq(state) && data === Seq(value))
    }
  }

  test("can send state to a sink returning a data stream with initial state") {
    forAll { (state: Int, value: String) =>
      val stateSink = new TestSink[Int]

      val data = runStream(mkStateSource[Int, String](Source.single(value)).passThroughData(state)(stateSink.sink))

      assert(stateSink.data === Seq(state) && data === Seq(value))
    }
  }

  test("can send data to a sink returning a state stream") {
    forAll { (state: Int, value: String) =>
      val dataSink = new TestSink[String]

      val data = runStream(mkStateSource[Int, String](Source.single(state -> value)).passThroughState(dataSink.sink))

      assert(dataSink.data === Seq(value) && data === Seq(state))
    }
  }

  test("can send data to a sink returning a state stream with initial state") {
    forAll { (state: Int, value: String) =>
      val dataSink = new TestSink[String]

      val data = runStream(mkStateSource[Int, String](Source.single(value)).passThroughState(state)(dataSink.sink))

      assert(dataSink.data === Seq(value) && data === Seq(state))
    }
  }

  override protected def afterAll(): Unit = {
    mat.shutdown()
    waitFor(as.terminate())
    super.afterAll()
  }
}

object IndexedStateSourceSuite {
  class TestSink[T] {
    private val _data: scala.collection.mutable.ArrayBuffer[T] = scala.collection.mutable.ArrayBuffer.empty

    def data: Seq[T] = Seq(_data: _*)

    lazy val sink: Sink[T, NotUsed] = Flow[T]
      .map { t =>
        _data += t
        t
      }
      .to(Sink.ignore)
  }

  val parallelism: Int = 3

  implicit def nelArb[A](implicit arb: Arbitrary[A]): Arbitrary[NonEmptyList[A]] =
    Arbitrary(for {
      head <- arb.arbitrary
      tail <- Gen.listOf(arb.arbitrary)
    } yield NonEmptyList.of(head, tail: _*))

  def repeat[A](v: A)(n: Int): Stream[A] = Stream.continually(v).take(n)

  def runStream[A](stream: Source[A, NotUsed])(implicit mat: ActorMaterializer): Seq[A] =
    waitFor(stream.toMat(Sink.seq)(Keep.right).run())

  def waitFor[A](fut: Future[A]): A = Await.result(fut, Duration.Inf)
}
