organization in ThisBuild := "com.thoughtworks.import"

libraryDependencies += "org.scala-lang" % "scala-compiler" % scalaVersion.value % Provided

libraryDependencies += "org.scalatest" %% "scalatest" % "3.0.3" % Test

scalacOptions in Test += "-Xplugin:" + (packageBin in Compile).value

crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1")

// Report bug
//incOptions := incOptions.value.withNameHashing(false).withNewClassfileManager()