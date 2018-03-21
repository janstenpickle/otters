package otters

trait StreamPipe[F[_], P[_, _]] extends Stream[F] {
  def via[A, B](fa: F[A])(pipe: P[A, B]): F[B]
}
