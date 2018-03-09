package statestream

trait StreamSink[F[_], G[_]] extends Stream[F] {
  def to[A, B](fa: F[A])(sink: Sink[F, G, A, B]): G[B]
}
