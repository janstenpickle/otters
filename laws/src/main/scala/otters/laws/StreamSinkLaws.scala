package otters.laws

import cats.laws._
import otters.{Sink, StreamSink}

trait StreamSinkLaws[F[_], G[_], H[_]] extends StreamLaws[F] {
  implicit override def F: StreamSink[F, G]

  def sinkApplication[A, B](fa: F[A], f: Sink[F, G, A, H[List[B]]]): IsEq[G[H[List[B]]]] =
    F.to(fa)(f) <-> f(fa)
}

object StreamSinkLaws {
  def apply[F[_], G[_], H[_]](implicit ev: StreamSink[F, G]): StreamSinkLaws[F, G, H] = new StreamSinkLaws[F, G, H] {
    implicit override def F: StreamSink[F, G] = ev
  }
}
