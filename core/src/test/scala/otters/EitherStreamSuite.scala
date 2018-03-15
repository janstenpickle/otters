package otters

import cats.data.{EitherT, NonEmptyList}
import otters.syntax.either._
import cats.syntax.either._
import cats.syntax.applicative._

trait EitherStreamSuite[F[_], G[_], H[_]] extends TestBase[F, G, H] {

  def mkEitherStream[A, B](src: F[Either[A, B]]): EitherT[F, A, B]
  def mkEitherStream[A](src: F[A], isLeft: A => Boolean): EitherT[F, A, A]
  def mkEitherStream[A, B, C](src: F[A], isLeft: A => Boolean, f: A => B, g: A => C): EitherT[F, B, C]
  def mkEitherStreamCatch[A, B](src: F[A], f: A => B): EitherT[F, Throwable, B]

  test("can make an eitherT from a stream of eithers") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret = runStream(mkEitherStream[Int, String](F.fromSeq(values.toList)).value)

      assert(ret === values.toList)
    }
  }

  test("can make an eitherT using a split function") {
    forAll { (values: NonEmptyList[Int]) =>
      val ret =
        runStream(mkEitherStream[Int](F.fromSeq(values.toList), _ > 2).value)

      assert(ret === values.map(i => if (i > 2) Left(i) else Right(i)).toList)
    }
  }

  test("can make an eitherT using a split function and mapping left and right") {
    forAll { (values: NonEmptyList[Int]) =>
      val ret =
        runStream(mkEitherStream[Int, String, Long](F.fromSeq(values.toList), _ > 2, _.toString, _.toLong).value)

      assert(ret === values.map(i => if (i > 2) Left(i.toString) else Right(i.toLong)).toList)
    }
  }

  test("can make an eitherT by catching non-fatal exceptions") {
    forAll { (values: NonEmptyList[Int]) =>
      val ret =
        runStream(
          mkEitherStreamCatch[Int, Int](F.fromSeq(values.toList), (i: Int) => throw new RuntimeException(i.toString)).value
        )

      assert(ret.map(_.leftMap(_.getMessage)) === values.map(i => Left(i.toString)).toList)
    }
  }

  test("can send data to different sinks") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val (left, right) =
        materialize(mkEitherStream[Int, String](F.fromSeq(values.toList)).toSinks(mkSeqSink, mkSeqSink)(_ -> _))

      assert(waitFor(left) === values.collect { case Left(a) => a } && waitFor(right) === values.collect {
        case Right(a) => a
      })
    }
  }

  test("can send data via separate pipes") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(mkEitherStream[Int, String](F.fromSeq(values.toList)).via(mkPipe(_.toString), mkPipe(_.length)).value)

      assert(ret === values.map(_.bimap(_.toString, _.length)).toList)
    }
  }

  test("can send data via left pipe") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(mkEitherStream[Int, String](F.fromSeq(values.toList)).leftVia(mkPipe(_.toString)).value)

      assert(ret === values.map(_.leftMap(_.toString)).toList)
    }
  }

  test("can send data via right pipe") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(mkEitherStream[Int, String](F.fromSeq(values.toList)).rightVia(mkPipe(_.length)).value)

      assert(ret === values.map(_.map(_.length)).toList)
    }
  }

  test("can map async with parallelism") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(mkEitherStream[Int, String](F.fromSeq(values.toList)).mapAsync(parallelism)(_.length.pure[G]).value)

      assert(ret === values.map(_.map(_.length)).toList)
    }
  }

  test("can map async") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(mkEitherStream[Int, String](F.fromSeq(values.toList)).mapAsync(_.length.pure[G]).value)

      assert(ret === values.map(_.map(_.length)).toList)
    }
  }

  test("can left map async with parallelism") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(
          mkEitherStream[Int, String](F.fromSeq(values.toList)).leftMapAsync(parallelism)(_.toString.pure[G]).value
        )

      assert(ret === values.map(_.leftMap(_.toString)).toList)
    }
  }

  test("can left map async") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(mkEitherStream[Int, String](F.fromSeq(values.toList)).leftMapAsync(_.toString.pure[G]).value)

      assert(ret === values.map(_.leftMap(_.toString)).toList)
    }
  }

  test("can flatMap async with parallelism") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(
          mkEitherStream[Int, String](F.fromSeq(values.toList))
            .flatMapAsync(parallelism)(s => EitherT.leftT[G, String](s.length))
            .value
        )

      assert(ret === values.map(_.flatMap(s => Left(s.length))).toList)
    }
  }

  test("can flatMap async") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(
          mkEitherStream[Int, String](F.fromSeq(values.toList))
            .flatMapAsync(s => EitherT.leftT[G, String](s.length))
            .value
        )

      assert(ret === values.map(_.flatMap(s => Left(s.length))).toList)
    }
  }

  test("can flatLeftMap async with parallelism") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(
          mkEitherStream[Int, String](F.fromSeq(values.toList))
            .flatLeftMapAsync(parallelism)(i => EitherT.rightT[G, Int](i.toString))
            .value
        )

      assert(ret === values.map(_.left.flatMap(i => Right(i.toString))).toList)
    }
  }

  test("can flatLeftMap async") {
    forAll { values: NonEmptyList[Either[Int, String]] =>
      val ret =
        runStream(
          mkEitherStream[Int, String](F.fromSeq(values.toList))
            .flatLeftMapAsync(i => EitherT.rightT[G, Int](i.toString))
            .value
        )

      assert(ret === values.map(_.left.flatMap(i => Right(i.toString))).toList)
    }
  }

}
