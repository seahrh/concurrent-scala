lazy val root = (project in file(".")).
  settings(
    inThisBuild(List(
      organization := "com.sgcharts",
      scalaVersion := "2.12.4",
      version      := "1.0.0"
    )),
    name := "concurrent-scala",
    libraryDependencies ++= Seq(
      "org.scalatest" %% "scalatest" % versions.scalatest % Test,
      "org.mockito" % "mockito-core" % versions.mockito % Test
    )
  )
lazy val versions = new {
  val scalatest = "3.0.4"
  val mockito = "2.13.0"
}
wartremoverErrors ++= Warts.allBut(
  Wart.Equals,
  Wart.ToString,
  Wart.Throw,
  Wart.DefaultArguments,
  Wart.Return,
  Wart.TraversableOps,
  Wart.ImplicitParameter,
  Wart.NonUnitStatements,
  Wart.Var
)
