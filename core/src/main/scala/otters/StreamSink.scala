package otters

trait StreamSink[F[_], G[_], S[_, _]] extends Stream[F] {
  def to[A, B](fa: F[A])(sink: S[A, B]): G[B]
}
