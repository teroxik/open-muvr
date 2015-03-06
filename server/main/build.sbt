import Dependencies._

Build.Settings.project

name := "main"

libraryDependencies ++= Seq(
  // Core Akka
  akka.actor,
  akka.cluster,
  akka.contrib,
  akka.persistence,
  akka.persistence_cassandra,
  akka.leveldb,
  // For REST API
  spray.httpx,
  spray.can,
  spray.routing,
  // Codec
  scodec_bits,
  scalaz.core,
  // Apple push notifications
  apns,
  slf4j.slf4j_simple,
  // Testing
  scalatest % "test",
  scalacheck % "test",
  akka.testkit % "test",
  spray.testkit % "test"
)

import DockerKeys._
import sbtdocker.ImageName
import sbtdocker.mutable.Dockerfile

dockerSettings

// Define a Dockerfile for:
//   - `LiftLocalMonolithApp`
//   - `LiftMonolithApp`

mainClass in assembly := Some("com.eigengo.lift.LiftMonolithApp")

docker <<= (docker dependsOn assembly)

dockerfile in docker := {
  val artifact = (outputPath in assembly).value
  val artifactTargetPath = s"/app/${artifact.name}"
  val debArtifactPath = s"${sourceDirectory.value}/../../debs"
  val debTargetPath = "/app/debs"
  new Dockerfile {
    from("dockerfile/java")
    val f = new File(s"${Path.userHome.absolutePath}/.ios")
    if (f.exists) add(f, "/root/.ios")
    add(artifact, artifactTargetPath)
    add(debArtifactPath, debTargetPath)
    run("apt-get", "update")
    // Install CVC4, JNI shared library/bindings - used by exercise classification models
    run("apt-get", "install", "-y", "--force-yes", "libantlr3c-3.2-0")
    run("dpkg", "-R", "-i", debTargetPath)
    entryPoint("java", "-jar", artifactTargetPath)
  }
}

imageName in docker := {
  ImageName(
    namespace = Some("janm399"),
    repository = "lift",
    tag = Some(s"${name.value}-production"))
}
