package statestream

import akka.NotUsed
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import cats.data.{NonEmptyList, State, StateT}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterAll, FunSuite}
import cats.instances.all._
import cats.kernel.Monoid
import org.scalacheck.{Arbitrary, Gen}

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}

class IndexedStateSourceSuite extends FunSuite with PropertyChecks with BeforeAndAfterAll {
  import IndexedStateSourceSuite._
  import StateSource.evalToFutureNat

  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ex: ExecutionContext = as.dispatcher

  test("can map over state") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(StateSource[Int, String](Source.single(value)).map(_.length).stream)

      val (st, fin) = ret.head.run(state).value

      assert(ret.size === 1 && st === state && fin === value.length)
    }
  }

  test("can mapAsync over state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(StateSource[Int, String](Source.single(value)).mapAsync(parallelism)(v => Future(v.length)).stream)

      val (st, fin) = ret.head.run(state).value

      assert(ret.size === 1 && st === state && fin === value.length)
    }
  }

  test("can flatMap state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(StateSource[Int, String](Source.single(value)).flatMap(v => State(s => (v.length + s, v))).stream)

      val (st, fin) = ret.head.run(state).value

      assert(ret.size === 1 && st === state + value.length && fin === value)
    }
  }

  test("can flatMapAsync state") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(
        StateSource[Int, String](Source.single(value))
          .flatMapAsync(parallelism)(v => StateT(s => Future(v.length + s, v)))
          .stream
      )

      val (st, fin) = ret.head.run(state).value

      assert(ret.size === 1 && st === state + value.length && fin === value)
    }
  }

  test("can modify state") {
    forAll { (state: Int, value: String) =>
      val ret =
        runStream(StateSource[Int, String](Source.single(value)).modify(_.toString).stream)

      val (st, fin) = ret.head.run(state).value

      assert(ret.size === 1 && st === state.toString && fin === value)
    }
  }

  test("can concatenate collection, putting state ") {
    forAll { (state: Int, value: String) =>
      val ret = runStream(StateSource[Int, String](Source.single(value)).transform(_.toString -> _.length).stream)

      val (st, fin) = ret.head.run(state).value

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
      val data = runConcat(Source.single(state -> values))(_.safeMapConcatHead(_.toList))
      val (headState, _) = data.head
      val tailState = data.tail.map(_._1).sum
      val returnValues = data.map(_._2)

      assert(headState === state && tailState === 0 && returnValues === values.map(Some(_)).toList)
    }
  }

  test("puts state on to the end of a stream when performing safeMapConcatTail") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(Source.single(state -> values))(_.safeMapConcatTail(_.toList))
      val (endState, _) = data.last
      val (front :+ _) = data.map(_._1)

      assert(endState === state && front.sum === 0 && data.map(_._2) === values.map(Some(_)).toList)
    }
  }

  test("copies state to each element of a stream when performing safeMapConcatAll") {
    forAll { (state: Int, values: NonEmptyList[String]) =>
      val data = runConcat(Source.single(state -> values))(_.safeMapConcatAll(_.toList))

      assert(
        data.map(_._1).sum === Stream.continually(state).take(values.size).sum && data
          .map(_._2) === values.map(Some(_)).toList
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
      val data = runStream(StateSource(Source.single(state -> value)).toTuple)

      assert(data === Seq(state -> value))
    }
  }

  test("converts state and data to a tuple when provided initial state") {
    forAll { (state: Int, value: String) =>
      val data = runStream(StateSource[Int, String](Source.single(value)).toTuple(state))

      assert(data === Seq(state -> value))
    }
  }

  test("can send state and data by independent flows") {
    forAll { (state: Int, value: String) =>
      val data = runStream(
        StateSource[Int, String](Source.single(state -> value))
          .via(Flow.fromFunction(_.toString), Flow.fromFunction(_.length))
          .stream
      )

      assert(data.map(_.runEmpty.value) === Seq(state.toString -> value.length))
    }
  }

  test("can send state by separate flows") {
    forAll { (state: Int, value: String) =>
      val data = runStream(
        StateSource[Int, String](Source.single(state -> value))
          .stateVia(Flow.fromFunction(_.toString))
          .stream
      )

      assert(data.map(_.runEmpty.value) === Seq(state.toString -> value))
    }
  }

  test("can send data by separate flow") {
    forAll { (state: Int, value: String) =>
      val data = runStream(
        StateSource[Int, String](Source.single(state -> value))
          .dataVia(Flow.fromFunction(_.length))
          .stream
      )

      assert(data.map(_.runEmpty.value) === Seq(state -> value.length))
    }
  }

//  test("can send state and data to separate sinks") {
//    forAll { (state: Int, value: String) =>
//      val (s, d) = StateSource[Int, String](Source.single(state -> value)).to(Sink.seq, Sink.seq)
//    }
//  }

  override protected def afterAll(): Unit = {
    mat.shutdown()
    waitFor(as.terminate())
    super.afterAll()
  }
}

object IndexedStateSourceSuite {
  val parallelism: Int = 3

  implicit def nelArb[A](implicit arb: Arbitrary[A]): Arbitrary[NonEmptyList[A]] =
    Arbitrary(for {
      head <- arb.arbitrary
      tail <- Gen.listOf(arb.arbitrary)
    } yield NonEmptyList.of(head, tail: _*))

  def repeat[A](v: A)(n: Int): Stream[A] = Stream.continually(v).take(n)

  def runConcat[S: Monoid, A, B](
    source: Source[(S, A), NotUsed]
  )(f: StateSource[S, A] => StateSource[S, B])(implicit mat: ActorMaterializer): Seq[(S, B)] =
    runStream(f(StateSource[S, A](source)).stream).map(_.runEmpty.value)

  def runStream[A](stream: Source[A, NotUsed])(implicit mat: ActorMaterializer): Seq[A] =
    waitFor(stream.toMat(Sink.seq)(Keep.right).run())

  def waitFor[A](fut: Future[A]): A = Await.result(fut, Duration.Inf)
}
