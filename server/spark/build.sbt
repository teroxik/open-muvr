/**
 * Based on https://github.com/kevinschmidt/docker-spark
 *
 * Currently uses spark 1.2.0, Scala 2.11
 *
 * If made dependent on common results on large amount of new dependency conflicts including akka etc.
 *
 * Large amount of files causes "Invalid or Corrupt jarfile is encountered" due to bug in java 7
 * Possible workarounds include use of java 8 (current solution) or startup using java -cp instead of java -jar
 * See http://stackoverflow.com/questions/18441076/why-java-complains-about-jar-files-with-lots-of-entries
 *
 */

import Dependencies._

Build.Settings.project

name := "spark"

scalaVersion := "2.11.4"

libraryDependencies ++= Seq(
  slf4j_simple,
  akkaAnalytics.cassandra,
  spark.core,
  spark.mllib
  //spark.streaming,
  //spark.streamingKafka
)

import DockerKeys._
import sbtdocker.ImageName
import sbtdocker.mutable.Dockerfile

dockerSettings

mainClass in assembly := Some("com.eigengo.lift.spark.Spark")

docker <<= (docker dependsOn assembly)

dockerfile in docker := {
  val artifact = (outputPath in assembly).value
  val artifactTargetPath = s"/app/${artifact.name}"
  new Dockerfile {
    from("martinz/spark-singlenode:latest")
    add(artifact, artifactTargetPath)
    entryPoint("/root/spark_singlenode_files/default_cmd")
    //entryPoint("java", "-jar", artifactTargetPath)
  }
}

imageName in docker := {
  ImageName(
    namespace = Some("janm399"),
    repository = "lift",
    tag = Some(name.value))
}