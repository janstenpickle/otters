package otters

import cats.{Always, Applicative, Eval, Traverse}

object SeqInstances {
  implicit val seqTraverse: Traverse[Seq] = new Traverse[Seq] {

    override def traverse[G[_], A, B](fa: Seq[A])(f: A => G[B])(implicit G: Applicative[G]): G[Seq[B]] =
      foldRight[A, G[Seq[B]]](fa, Always(G.pure(Seq.empty))) { (a, lglb) =>
        G.map2Eval(f(a), lglb)(_ +: _)
      }.value

    override def foldLeft[A, B](fa: Seq[A], b: B)(f: (B, A) => B): B = fa.foldLeft(b)(f)

    override def foldRight[A, B](fa: Seq[A], lb: Eval[B])(f: (A, Eval[B]) => Eval[B]): Eval[B] = {
      def loop(as: Seq[A]): Eval[B] =
        as match {
          case Seq() => lb
          case h +: t => f(h, Eval.defer(loop(t)))
        }
      Eval.defer(loop(fa))
    }
  }
}
