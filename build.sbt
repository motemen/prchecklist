import org.scalatra.sbt.ScalatraPlugin
import com.typesafe.sbt.SbtScalariform
import NativePackagerHelper._

val stylesheetsDirectory = settingKey[File]("Directory where generated stylesheets are placed")
val scriptsDirectory = settingKey[File]("Directory where generated script files are placed")
val npmInstall = taskKey[Unit]("Run `npm install`")
val npmRunBuild = taskKey[Set[File]]("Run `npm run build`")
val npmRunWatch = inputKey[Unit]("Run `npm run watch`")

val commonSettings = Seq(
  organization := "net.tokyoenvious",
  scalaVersion := "2.11.7",
  version := {
    ("git describe --tags --match v* --dirty=-SNAPSHOT --always" !!) trim
  },

  scalacOptions ++= Seq(
    "-unchecked",
    "-deprecation",
    "-feature"
  ),

  resolvers += Classpaths.typesafeReleases
)

lazy val core = (project in file("core")).
  settings(commonSettings: _*).
  settings(
    name := "prchecklist-core",

    libraryDependencies ++= Seq(
      "org.scalaj" %% "scalaj-http" % "1.1.6",
      "org.json4s" %% "json4s-native" % "3.3.0",
      "com.typesafe.slick" %% "slick" % "3.0.0",
      "org.postgresql" % "postgresql" % "9.4.1207",
      "com.github.tarao" %% "slick-jdbc-extension" % "0.0.3",
      "net.debasishg" %% "redisclient" % "3.1",
      "org.mockito" % "mockito-core" % "2.0.36-beta" % "test",
      "org.scalatest" %% "scalatest" % "2.2.6" % "test",
      "com.fasterxml.jackson.dataformat" % "jackson-dataformat-yaml" % "2.5.4",
      "com.fasterxml.jackson.module" %% "jackson-module-scala" % "2.7.3",
      "commons-codec" % "commons-codec" % "1.10",
      "ch.qos.logback" % "logback-classic" % "1.1.2" % "runtime"
    )
  )

lazy val root = (project in file(".")).
  dependsOn(core % "test->test;compile->compile").
  enablePlugins(
    BuildInfoPlugin,
    JavaAppPackaging
  ).
  settings(ScalatraPlugin.scalatraSettings).
  settings(SbtScalariform.scalariformSettings).
  settings(commonSettings: _*).
  settings(
    name := "prchecklist",

    libraryDependencies ++= Seq(
      "org.scalatra" %% "scalatra" % "2.4.0",
      "org.scalatra" %% "scalatra-scalatest" % "2.4.0" % "test",
      "ch.qos.logback" % "logback-classic" % "1.1.2" % "runtime",
      "org.eclipse.jetty" % "jetty-webapp" % "9.2.10.v20150310" % "container;compile",
      "javax.servlet" % "javax.servlet-api" % "3.1.0" % "provided",
      "org.pegdown" % "pegdown" % "1.6.0",
      "org.mockito" % "mockito-core" % "2.0.36-beta" % "test"
    )
  ).
  settings(
    // sourceGenerators in Compile <+= buildInfo in Compile,
    buildInfoKeys := Seq[BuildInfoKey](
      name, version, scalaVersion, sbtVersion
    ),
    buildInfoOptions += BuildInfoOption.BuildTime,
    buildInfoPackage := "prchecklist"
  ).
  settings (
    npmInstall := {
      val log = streams.value.log
      val npmInstall = FileFunction.cached(cacheDirectory.value / "npm-install") (FilesInfo.hash, FilesInfo.exists) {
        (changeReport, in) =>
          log.info("Running 'npm install' ...")
          (("npm" :: "install" :: Nil) ! log) ensuring (_ == 0)
          log.info("Done 'npm install'.")

          Set.empty[File]
      }

      npmInstall(Set(baseDirectory.value / "package.json"))
    },

    npmRunBuild := {
      val s = streams.value

      val npmRunBuild = FileFunction.cached(cacheDirectory.value / "npm-build") (FilesInfo.hash, FilesInfo.exists) {
        (changeReport, in) =>
          s.log.info("Running 'npm run build' ...")
          (("npm" :: "run" :: "build" :: Nil) ! s.log) ensuring (_ == 0)
          s.log.info("Done 'npm run build'.")

        Set(
          stylesheetsDirectory.value / "main.css",
          stylesheetsDirectory.value / "main.css.map",
          scriptsDirectory.value / "app.js"
        )
      }

      // TODO do this caching on the npm/node layer
      npmRunBuild(
        Set(
          baseDirectory.value / "src/main/less/main.less",
          baseDirectory.value / "src/main/typescript/app.tsx",
          baseDirectory.value / "src/main/typescript/ChecklistComponent.tsx"
        )
      )
    },

    npmRunBuild <<= npmRunBuild.dependsOn(npmInstall),

    npmRunWatch := {
      val x = processStart.fullInput(" ./node_modules/.bin/exor npm run watch").evaluated
    },

    npmRunWatch <<= npmRunWatch.dependsOn(npmInstall),

    update <<= (update, npmInstall) map {
      (report, _) =>
        report
    }
  ).
  settings(
    // We could have used webappSrc key provided by xsbt-web-plugin,
    // but it is a TaskKey which a SettingKey cannot depend on.
    stylesheetsDirectory := (sourceDirectory in Compile).value / "webapp" / "stylesheets", /* src/main/webapp/stylesheets */
    scriptsDirectory := (sourceDirectory in Compile).value / "webapp" / "scripts", /* src/main/webapp/scripts */
    resourceGenerators in Compile <+= npmRunBuild.map(_.toSeq),
    cleanFiles ++= Seq(stylesheetsDirectory.value, scriptsDirectory.value / "app.js"),
    mappings in Universal <++= (stylesheetsDirectory, baseDirectory, resources in Compile) map {
      (stylesheetsDirectory, baseDirectory, _) =>
        stylesheetsDirectory.*** x relativeTo(baseDirectory)
    },
    mappings in Universal <++= (scriptsDirectory, baseDirectory, resources in Compile) map {
      (scriptsDirectory, baseDirectory, _) =>
        scriptsDirectory.*** x relativeTo(baseDirectory)
    }
 )

addCommandAlias("devel", Seq(
  "set javaOptions += \"-DbrowserSync.port=3000\"",
  "npmRunWatch",
  "~re-start"
).mkString(";", ";", ""))

watchSources ~= {
  _.filterNot {
    f =>
      f.getName matches """.*\.(less|css(\.map)?|js)$"""
  }
}
