name := "scarlett"

version := "1.0"

scalaVersion := "2.13.6"

val akkaVersion = "2.6.14"

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor" % akkaVersion,
  "com.typesafe.akka" %% "akka-testkit" % akkaVersion % Test
) ++ Seq(
  "org.scodec" %% "scodec-bits" % "1.1.27",
  "org.scodec" %% "scodec-core" % "1.11.8",
  "org.scodec" %% "scodec-stream" % "3.0.1"
) ++ Seq("org.scalatest" %% "scalatest" % "3.1.4" % Test)
