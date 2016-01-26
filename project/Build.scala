import sbt._
import Keys._

import scala.sys.process.Process

import java.lang.Runtime

object ProcessManager {
  val all = scala.collection.mutable.Map.empty[Seq[String], Process]

  def startProcess[P](command: Seq[String]): Unit = {
    all.synchronized {
      all.getOrElseUpdate(command, Process(command).run())
    }
  }

  def stopAllProcesses(): Unit = {
    all.synchronized {
      all.foreach {
        case (command, process) =>
          println(s"Stopping ${command.mkString(" ")} ...")
          process.destroy()
          process.exitValue()
      }
      all.clear()
    }
  }
}

import complete.DefaultParsers._

object Build extends Build {
  val processStart = inputKey[Unit]("Starts a new process")
  val processStopAll = taskKey[Unit]("Stops all processes")

  override lazy val settings = super.settings ++ Seq(
    onUnload in Global ~= {
      onUnload =>
        state =>
          ProcessManager.stopAllProcesses()
          onUnload(state)
    },

    processStart := {
      val command = spaceDelimited("<command>").parsed
      ProcessManager.startProcess(command)
    },

    processStopAll := {
      ProcessManager.stopAllProcesses()
    }
  )

  Runtime.getRuntime.addShutdownHook(
    new Thread(new Runnable {
      override def run() = {
        ProcessManager.stopAllProcesses()
      }
    })
  )
}
