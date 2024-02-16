import scala.collection.Seq

name := "cluster-scala"
version := "1.0"
scalaVersion := "2.13.12"
resolvers += "Akka library repository".at("https://repo.akka.io/maven")

// Note: this default isn't really used anywhere so not important to bump
lazy val akkaVersion = sys.props.getOrElse("akka.version", "2.9.1")

fork := true

// useful for investigations, needs to be run on graalvm JDK
// javaOptions += "-agentlib:native-image-agent=config-output-dir=target/generated-native-image-metadata"
javaOptions ++= Seq(
  // LMDB / ddata durable store
  "--add-opens=java.base/sun.nio.ch=ALL-UNNAMED",
  "--add-opens=java.base/java.nio=ALL-UNNAMED")

// GraalVM native image build
enablePlugins(NativeImagePlugin)
nativeImageJvm := "graalvm-community"
nativeImageVersion := "21.0.2"
nativeImageOptions := Seq(
  "--no-fallback",
  "--verbose",
  "-Dakka.native-image.debug=true",
  // FIXME I can't seem to get this to work, need it to verify no errors were logged in CI
  "--initialize-at-build-time=ch.qos.logback,org.slf4j.LoggerFactory,org.slf4j.MDC",
  "-Dlogback.configurationFile=logback-native-image.xml" // configured at build time
)

libraryDependencies ++= Seq(
  "com.typesafe.akka" %% "akka-actor-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-stream" % akkaVersion,
  "com.typesafe.akka" %% "akka-serialization-jackson" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-typed" % akkaVersion,
  "com.typesafe.akka" %% "akka-cluster-sharding-typed" % akkaVersion,
  "ch.qos.logback" % "logback-classic" % "1.2.13",
  "com.typesafe.akka" %% "akka-actor-testkit-typed" % akkaVersion % Test,
  "org.scalatest" %% "scalatest" % "3.2.15" % Test)
