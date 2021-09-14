ThisBuild / organization := "org.um"
ThisBuild / scalaVersion := "2.12.11"
ThisBuild / version 	 := "0.1.0-SNAPSHOT"
Compile   / mainClass    := Some("org.um.streaming.TraceConsumer")
Compile   / resourceDirectory := baseDirectory.value / "config"

val scala = "org.scala-lang" % "scala-library" % "2.12.11" 
val scala_test = "org.scalatest" %% "scalatest" % "3.0.6"
val scala_logging = "com.typesafe.scala-logging" %% "scala-logging" % "3.9.2"
val spark_core = "org.apache.spark" %% "spark-core" % "2.4.5"
val spark_streaming = "org.apache.spark" %% "spark-streaming" % "2.4.5"
val spark_streaming_kafka = "org.apache.spark" %% "spark-streaming-kafka-0-10" % "2.4.5"
val play = "com.typesafe.play" %% "play" % "2.8.1"
val config = "com.typesafe" % "config" % "1.2.1"

lazy val root = (project in file("."))
	.settings(
		name := "5g-real-time-ddos-detection",
		libraryDependencies ++= Seq(scala,
					    scala_test,
					    scala_logging,
					    spark_core,
					    spark_streaming,
					    spark_streaming_kafka,
					    play,
					    config)
		)

