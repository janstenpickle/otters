val commonSettings = Seq(
  organization := "io.stream-state",
  scalaVersion := "2.12.4",
  addCompilerPlugin(("org.spire-math"  % "kind-projector" % "0.9.4").cross(CrossVersion.binary)),
  addCompilerPlugin(("org.scalamacros" % "paradise"       % "2.1.0").cross(CrossVersion.full)),
  scalacOptions ++= Seq(
    "-unchecked",
    "-feature",
    "-deprecation:false",
    "-Xcheckinit",
    "-Xlint:-nullary-unit",
    "-Ywarn-numeric-widen",
    "-Ywarn-dead-code",
    "-Yno-adapted-args",
    "-Ypartial-unification",
    "-language:_",
    "-target:jvm-1.8",
    "-encoding",
    "UTF-8"
  ),
  publishMavenStyle := true,
  licenses += ("MIT", url("http://opensource.org/licenses/MIT")),
  homepage := Some(url("https://github.com/janstenpickle/extruder")),
  developers := List(
    Developer(
      "janstenpickle",
      "Chris Jansen",
      "janstenpickle@users.noreply.github.com",
      url = url("https://github.com/janstepickle")
    )
  ),
  publishArtifact in Test := false,
  pomIncludeRepository := { _ =>
    false
  },
  bintrayReleaseOnPublish := false,
  coverageMinimum := 85,
  releaseCrossBuild := true,
  scalafmtOnCompile := true,
  scalafmtTestOnCompile := true,
  releaseIgnoreUntrackedFiles := true
)

lazy val root = (project in file("."))
  .settings(commonSettings)
  .settings(name := "stream-state", publishArtifact := false)
  .aggregate(core, akka, fs2, monix)

lazy val core = (project in file("core"))
  .settings(commonSettings)
  .settings(
    name := "stream-state-core",
    libraryDependencies ++= Seq(
      Dependencies.cats,
      Dependencies.simulacrum,
      Dependencies.scalaCheck % Test,
      Dependencies.scalaTest  % Test
    ),
    publishArtifact in Test := true,
    coverageEnabled.in(Test, test) := true
  )

lazy val laws = (project in file("laws"))
  .settings(commonSettings)
  .settings(
    name := "stream-state-laws",
    libraryDependencies ++= Seq(
      Dependencies.cats,
      Dependencies.catsLaws,
      Dependencies.catsEffectLaws,
      Dependencies.discipline,
      Dependencies.scalaCheck,
      Dependencies.scalaTest
    ),
    publishArtifact in Test := true,
    coverageEnabled.in(Test, test) := true
  )
  .dependsOn(core)

lazy val akka = (project in file("akka"))
  .settings(commonSettings)
  .settings(
    name := "stream-state-akka",
    libraryDependencies ++= Seq(
      Dependencies.akkaStreams,
      Dependencies.discipline,
      Dependencies.catsEffectLaws % Test,
      Dependencies.scalaCheck     % Test,
      Dependencies.scalaTest      % Test
    ),
    publishArtifact in Test := true,
    coverageEnabled.in(Test, test) := true
  )
  .dependsOn(core % "compile->compile;test->test", laws % "test->compile")

lazy val fs2 = (project in file("fs2"))
  .settings(commonSettings)
  .settings(
    name := "stream-state-fs2",
    libraryDependencies ++= Seq(Dependencies.fs2, Dependencies.scalaCheck % Test, Dependencies.scalaTest % Test),
    publishArtifact in Test := true,
    coverageEnabled.in(Test, test) := true
  )
  .dependsOn(core % "compile->compile;test->test", laws % "test->compile")

lazy val monix = (project in file("monix"))
  .settings(commonSettings)
  .settings(
    name := "stream-state-fs2",
    libraryDependencies ++= Seq(Dependencies.monix, Dependencies.scalaCheck % Test, Dependencies.scalaTest % Test),
    publishArtifact in Test := true,
    coverageEnabled.in(Test, test) := true
  )
  .dependsOn(core % "compile->compile;test->test", laws % "test->compile")
