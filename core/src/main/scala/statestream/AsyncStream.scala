package statestream

trait AsyncStream[F[_], G[_]] extends Stream[F] {
  def mapAsync[A, B](fa: F[A])(f: A => G[B]): F[B]
  def mapAsyncN[A, B](fa: F[A])(parallelism: Int)(f: A => G[B]): F[B]
}
