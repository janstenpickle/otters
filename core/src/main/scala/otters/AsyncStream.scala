package otters

trait AsyncStream[F[_], G[_]] extends Stream[F] {

  /**
    * Maps elements from the source using a function that can do
    * asynchronous processing
    */
  def mapAsync[A, B](fa: F[A])(f: A => G[B]): F[B]

  /**
    * Maps elements from the source using a function that can do
    * asynchronous processing across n threads
    *
    * @param parallelism number of threads on which to execute the processing
    */
  def mapAsyncN[A, B](fa: F[A])(parallelism: Int)(f: A => G[B]): F[B]
}

object AsyncStream {
  trait AsyncStreamOps {
    implicit class AsyncStreamSyntax[F[_], A](stream: F[A]) {
      def mapAsync[G[_], B](f: A => G[B])(implicit F: AsyncStream[F, G]): F[B] = F.mapAsync(stream)(f)
      def mapAsync[G[_], B](parallelism: Int)(f: A => G[B])(implicit F: AsyncStream[F, G]): F[B] =
        F.mapAsyncN(stream)(parallelism)(f)
    }
  }
}
