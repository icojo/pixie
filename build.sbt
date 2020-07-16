// *****************************************************************************
// Library dependencies
// *****************************************************************************

lazy val library =
  new {

    object Version {
      val zio          = "1.0.0-RC20"
      val zioConfig    = "1.0.0-RC20"
      val sparkVersion = "3.0.0"
    }

    val zio        = "dev.zio" %% "zio"          % Version.zio
    val zioTest    = "dev.zio" %% "zio-test"     % Version.zio
    val zioTestSbt = "dev.zio" %% "zio-test-sbt" % Version.zio

    val zioConfig         = "dev.zio" %% "zio-config"          % Version.zioConfig
    val zioConfigMagnolia = "dev.zio" %% "zio-config-magnolia" % Version.zioConfig

    val sparkDependencies = Seq("spark-core", "spark-sql", "spark-streaming")
      .map("org.apache.spark" %% _ % Version.sparkVersion)
  }

// *****************************************************************************
// Projects
// *****************************************************************************

lazy val root =
  project
    .in(file("."))
    .settings(settings)
    .settings(
      libraryDependencies ++= Seq(
        library.zio,
        library.zioConfig,
        library.zioConfigMagnolia,
        "org.polynote" %% "uzhttp" % "0.2.4",
        library.zioTest    % Test,
        library.zioTestSbt % Test
      ) ++ library.sparkDependencies,
      testFrameworks += new TestFramework("zio.test.sbt.ZTestFramework")
    )

// *****************************************************************************
// Settings
// *****************************************************************************

lazy val settings =
  commonSettings ++
    commandAliases

lazy val commonSettings =
  Seq(
    name := "pixie",
    scalaVersion := "2.12.10",
    organization := "com.example"
  )

lazy val commandAliases =
  addCommandAlias("fmt", "all scalafmtSbt scalafmt test:scalafmt") ++
    addCommandAlias("check", "all scalafmtSbtCheck scalafmtCheck test:scalafmtCheck")

// *****************************************************************************
// Scalac
// *****************************************************************************

lazy val stdOptions = Seq(
  "-encoding",
  "UTF-8",
  "-explaintypes",
  "-Yrangepos",
  "-feature",
  "-language:higherKinds",
  "-language:existentials",
  "-Xlint:_,-type-parameter-shadow",
  "-Xsource:2.13",
  "-Ywarn-numeric-widen",
  "-Ywarn-value-discard",
  "-unchecked",
  "-deprecation",
  "-Xfatal-warnings"
)

lazy val stdOptsUpto212 = Seq(
  "-Xfuture",
  "-Ypartial-unification",
  "-Ywarn-nullary-override",
  "-Yno-adapted-args",
  "-Ywarn-infer-any",
  "-Ywarn-inaccessible",
  "-Ywarn-nullary-unit",
  "-Ywarn-unused-import"
)

scalacOptions := stdOptions ++ Seq(
  "-opt-warnings",
  "-Ywarn-extra-implicit",
  "-Ywarn-unused:_,imports",
  "-Ywarn-unused:imports",
  "-opt-inline-from:<source>"
) ++ stdOptsUpto212
