package prchecklist

import scala.concurrent.Await
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration.Duration

package object utils {
  implicit class RunnableFuture[A](fut: Future[A]) {
    def run: A = Await.result(fut, Duration.Inf)
  }
}
