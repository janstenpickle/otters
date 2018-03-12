package statestream.syntax.akkastream

import akka.stream.scaladsl.RunnableGraph
import statestream.instances.akkastream.Src
import statestream.syntax.WriterStreamOps

import scala.concurrent.Future

trait AkkaStreamSyntax extends WriterStreamOps {
  override type Stream[A] = Src[A]
  override type Async[A] = Future[A]
  override type PreMat[A] = RunnableGraph[A]
}
