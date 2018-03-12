package statestream.instances

import akka.NotUsed
import akka.stream.scaladsl.{RunnableGraph, Source}
import cats.Id
import statestream.WriterStream

import scala.concurrent.Future

package object akkastream extends AkkaStreamInstances {
  type Src[A] = Source[A, NotUsed]
  type AkkaWriterStream[S, A] = WriterStream[Src, Id, Future, RunnableGraph, S, A]
  type AkkaWriterStreamT[F[_], S, A] = WriterStream[Src, F, Future, RunnableGraph, S, A]
}
