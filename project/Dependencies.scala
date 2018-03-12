import sbt._

object Dependencies {
  lazy val scalaTest = "org.scalatest"         %% "scalatest"        % "3.0.4"
  lazy val scalaCheck = "org.scalacheck"       %% "scalacheck"       % "1.13.5"
  lazy val akkaStreams = "com.typesafe.akka"   %% "akka-stream"      % "2.5.11"
  lazy val cats = "org.typelevel"              %% "cats-core"        % "1.0.1"
  lazy val catsLaws = "org.typelevel"          %% "cats-laws"        % "1.0.1"
  lazy val catsEffectLaws = "org.typelevel"    %% "cats-effect-laws" % "0.9"
  lazy val discipline = "org.typelevel"        %% "discipline"       % "0.8"
  lazy val monixReactive = "io.monix"          %% "monix-reactive"   % "3.0.0-M3"
  lazy val monixTail = "io.monix"              %% "monix-tail"       % "3.0.0-M3"
  lazy val fs2 = "co.fs2"                      %% "fs2-io"           % "0.10.1"
  lazy val simulacrum = "com.github.mpilquist" %% "simulacrum"       % "0.12.0"
}
