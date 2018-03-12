package otters.syntax.akkastream

import akka.stream.scaladsl.RunnableGraph
import otters.instances.akkastream.Src
import otters.syntax.WriterStreamOps

import scala.concurrent.Future

trait AkkaStreamSyntax extends WriterStreamOps {
  override type Stream[A] = Src[A]
  override type Async[A] = Future[A]
  override type PreMat[A] = RunnableGraph[A]
}
