resolvers += Classpaths.typesafeResolver
// resolvers += "Nexus Repository" at "https://nexus3.shared.wmg.com/repository/public/"
resolvers += "Local Maven Repository" at "file:///C:/Users/ssnit/.m2/repository"


// these comment markers are for including code into the docs
//#sbt-multi-jvm
addSbtPlugin("com.typesafe.sbt" % "sbt-multi-jvm" % "0.3.8")
//#sbt-multi-jvm

addSbtPlugin("com.typesafe.sbt" % "sbt-scalariform" % "1.2.0")

addSbtPlugin("com.typesafe.sbt" % "sbt-site" % "0.7.1")

addSbtPlugin("com.typesafe.sbt" % "sbt-osgi" % "0.6.0")

addSbtPlugin("com.typesafe" % "sbt-mima-plugin" % "0.1.6")

addSbtPlugin("com.typesafe.sbt" % "sbt-pgp" % "0.8.3")

addSbtPlugin("com.typesafe.sbt" % "sbt-s3" % "0.5")

addSbtPlugin("pl.project13.scala" % "sbt-jmh" % "0.1.14")

// needed for the akka-sample-hello-kernel
// it is also defined in akka-samples/akka-sample-hello-kernel/project/plugins.sbt
//addSbtPlugin("com.typesafe.sbt" % "sbt-native-packager" % "0.8.0-M2")

libraryDependencies += "com.timgroup" % "java-statsd-client" % "2.0.0"
libraryDependencies += "com.wmg.dsp.tango.royalties.tools" % "tango-royalty-datapipeline" % "1.2.57-POSTGRES-POC-SNAPSHOT" excludeAll(
        ExclusionRule(organization = "com.wordnik")
      ) excludeAll(
        ExclusionRule(organization = "com.typesafe.akka")
      ) excludeAll(
        ExclusionRule(organization = "com.datastax.cassandra")
      ) excludeAll(
        ExclusionRule(organization = "org.springframework")
      ) excludeAll(
        ExclusionRule(organization = "io.kamon")
      ) excludeAll(
        ExclusionRule(organization = "org.apache.cassandra")
      ) exclude("com.wmg.dsp.tango", "akka-persistence-cassandra-12_2.10")


libraryDependencies += "com.github.etaty" %% "rediscala" % "1.7.0"

addSbtPlugin("io.spray" % "sbt-boilerplate" % "0.5.9")

