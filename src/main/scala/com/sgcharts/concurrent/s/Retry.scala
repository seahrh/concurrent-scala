package com.sgcharts.concurrent.s

import scala.annotation.tailrec
import scala.concurrent.{Await, ExecutionContext, Future, Promise, blocking}
import scala.concurrent.duration.{Deadline, Duration, DurationLong}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try, Random}

object Retry {

  class TooManyRetriesException extends RuntimeException

  class DeadlineExceededException extends RuntimeException

  private val minBackoff: Duration = 100 milliseconds

  private def exponentialBackoffMultiplier(retryCnt: Int): Long = scala.math.pow(2, retryCnt).round

  /**
    * exponential back off for retry
    */
  def exponentialBackoff(retryCnt: Int): Duration = {
    exponentialBackoffMultiplier(retryCnt) * minBackoff
  }

  def exponentialBackoffAtMostOneHour(retryCnt: Int): Duration = {
    val max: Duration = 1 hours
    val d: Duration = exponentialBackoff(retryCnt)
    if (d < max) d else max
  }

  def exponentialBackoffWithJitter(retryCnt: Int): Duration = {
    val jitter: Duration = Random.nextFloat * minBackoff
    exponentialBackoffMultiplier(retryCnt) * (minBackoff + jitter)
  }

  private def doNotGiveUp(t: Throwable): Boolean = false

  /**
    * retry a particular block that can fail
    *
    * @param maxRetry          how many times to retry before to giveup
    * @param deadline          how long to retry before giving up; default None
    * @param backoff           a back-off function that returns a Duration after which to retry.
    *                          Default is an exponential backoff at 100 milliseconds steps
    * @param giveUpOnThrowable if you want to stop retrying on a particular exception
    * @param block             a block of code to retry
    * @param executionContext  an execution context where to execute the block
    * @return an eventual Future succeeded with the value computed or failed with one of:
    *         `TooManyRetriesException`
    *         if there were too many retries without an exception being caught.
    *         Probably impossible if you pass decent parameters
    *         `DeadlineExceededException` if the retry didn't succeed before the provided deadline
    *         `Throwable` the last encountered exception
    */
  def retry[T](maxRetry: Int,
               deadline: Option[Deadline] = None,
               backoff: (Int) => Duration = exponentialBackoff,
               giveUpOnThrowable: Throwable => Boolean = doNotGiveUp)
              (block: => T)
              (implicit executionContext: ExecutionContext): Future[T] = {
    val p = Promise[T]()

    @tailrec
    def recursiveRetry(
                        retryCnt: Int,
                        exception: Option[Throwable]
                      )(f: () => T): Option[T] = {
      val isOverdue: Boolean = deadline.fold(false) {
        _.isOverdue()
      }
      val isTooManyRetries: Boolean = maxRetry >= 0 && maxRetry < retryCnt
      if (isOverdue) {
        val e = new DeadlineExceededException
        for (cause <- exception) {
          e.initCause(cause)
        }
        p failure e
        None
      } else if (isTooManyRetries) {
        val e = new TooManyRetriesException
        for (cause <- exception) {
          e.initCause(cause)
        }
        p failure e
        None
      } else {
        Try {
          deadline match {
            case Some(d) =>
              Await.result(Future {
                f()
              }, d.timeLeft)
            case _ => f()
          }
        } match {
          case Success(v) =>
            p success v
            Option(v)
          case Failure(t) if !giveUpOnThrowable(t) =>
            blocking {
              val interval = backoff(retryCnt).toMillis
              Thread.sleep(interval)
            }
            recursiveRetry(retryCnt + 1, Option(t))(f)
          case Failure(t) =>
            p failure t
            None
        }
      }
    }

    def doBlock() = block

    Future[Unit] {
      recursiveRetry(0, None)(doBlock)
    }
    p.future
  }
}


