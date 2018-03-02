package stateflow

import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}
import org.scalatest.prop.PropertyChecks
import org.scalatest.{BeforeAndAfterAll, FunSuite}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, ExecutionContext, Future}

class IndexedStateFlowSuite extends FunSuite with PropertyChecks with BeforeAndAfterAll {
  import IndexedStateFlowSuite._

  implicit val as: ActorSystem = ActorSystem()
  implicit val mat: ActorMaterializer = ActorMaterializer()
  implicit val ex: ExecutionContext = as.dispatcher

  test("can map") {
    forAll { (state: Int, value: String) =>
      val ret = waitFor(
        StateFlow[List[Int], String](Source.single(value)).map(_.length).stream.toMat(Sink.seq)(Keep.right).run()
      )

      val (st, fin) = ret.head.run(List(state)).value

      assert(ret.size === 1 && st === List(state) && fin === value.length)
    }
  }

  override protected def afterAll(): Unit = {
    mat.shutdown()
    waitFor(as.terminate())
    super.afterAll()
  }
}

object IndexedStateFlowSuite {
  def waitFor[A](fut: Future[A]): A = Await.result(fut, Duration.Inf)
}
