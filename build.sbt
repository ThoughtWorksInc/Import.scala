organization in ThisBuild := "com.thoughtworks.import"

libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % Test

scalacOptions in Test += "-Xplugin:" + (packageBin in Compile).value

crossScalaVersions := Seq("2.11.11", "2.12.2")
