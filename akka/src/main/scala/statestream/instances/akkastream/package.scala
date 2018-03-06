package statestream.instances

import akka.NotUsed
import akka.stream.scaladsl.{RunnableGraph, Source}
import cats.Eval
import statestream.IndexedStateStream

import scala.concurrent.Future

package object akkastream extends AkkaStreamInstances {
  type Src[A] = Source[A, NotUsed]
  type StateStream[S, A] = IndexedStateStream[Src, Eval, Future, RunnableGraph, S, S, A]
}
