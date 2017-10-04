package retry

import scala.annotation.tailrec
import scala.concurrent.{Await, ExecutionContext, Future, Promise, blocking}
import scala.concurrent.duration.{Deadline, Duration, DurationLong}
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}

object Retry {

  class TooManyRetriesException extends Exception("too many retries without exception")

  class DeadlineExceededException extends Exception("deadline exceeded")

  /**
    * exponential back off for retry
    */
  private def exponentialBackoff(r: Int): Duration = scala.math.pow(2, r).round * 100 milliseconds

  private def ignore(t: Throwable): Boolean = true

  /**
    * retry a particular block that can fail
    *
    * @param maxRetry         how many times to retry before to giveup
    * @param deadline         how long to retry before giving up; default None
    * @param backoff          a back-off function that returns a Duration after which to retry.
    *                         Default is an exponential backoff at 100 milliseconds steps
    * @param ignoreThrowable  if you want to stop retrying on a particular exception
    * @param block            a block of code to retry
    * @param executionContext an execution context where to execute the block
    * @return an eventual Future succeeded with the value computed or failed with one of:
    *         `TooManyRetriesException`
    *         if there were too many retries without an exception being caught.
    *         Probably impossible if you pass decent parameters
    *         `DeadlineExceededException` if the retry didn't succeed before the provided deadline
    *         `TimeoutException` if you provide a deadline
    *         and the block takes too long to execute
    *         `Throwable` the last encountered exception
    */
  def retry[T](maxRetry: Int,
               deadline: Option[Deadline] = None,
               backoff: (Int) => Duration = exponentialBackoff,
               ignoreThrowable: Throwable => Boolean = ignore)
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
      if (maxRetry == retryCnt || isOverdue) {
        exception match {
          case Some(t) =>
            p failure t
          case None if isOverdue =>
            p failure new DeadlineExceededException
          case None =>
            p failure new TooManyRetriesException
        }
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
          case Failure(t) if ignoreThrowable(t) =>
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


