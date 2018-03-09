package statestream

import cats.kernel.Monoid
import cats.{~>, Bimonad, Eval, Id, Monad}

class IndexedStateStreamNatSeqSuite extends IndexedStateStreamSeqSuite[Eval] {
  override implicit def G: Monad[Eval] = implicitly[Bimonad[Eval]]
  override implicit def nat: ~>[Eval, Id] = new ~>[Eval, Id] {
    override def apply[A](fa: Eval[A]): Id[A] = fa.value
  }

  override def mkStateStream[S, A](src: Seq[A]): IndexedStateStream[Seq, Eval, Id, Id, S, S, A] =
    IndexedStateStreamNat(src)

  override def mkStateStream[S: Monoid, A](src: Seq[(S, A)]): IndexedStateStream[Seq, Eval, Id, Id, S, S, A] =
    IndexedStateStreamNat(src)

  override def extract[A](fa: Eval[A]): A = fa.value

}
