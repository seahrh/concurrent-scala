package com.sgcharts.concurrent.scala

import com.sgcharts.concurrent.scala.Retry._
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mockito._
import org.scalatest.FlatSpec
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.{Deadline, Duration, DurationLong, fromNow}
import scala.concurrent.{Await, Future}
import scala.language.postfixOps

class RetrySpec extends FlatSpec with MockitoSugar {
  private val waitAtMost: Duration = 4 second

  class Bar {
    def inc(i: Int): Int = i + 1

    def incWithDelay(i: Int): Int = {
      Thread.sleep(100) // scalastyle:ignore
      inc(i)
    }
  }

  private def giveUpOnIllegalArgumentException(t: Throwable): Boolean = t match {
    case _: IllegalArgumentException => true
    case _ => false
  }

  "Future" should "return value if block succeeds without retry" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo).thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    assertResult(2)(Await.result(f, waitAtMost))
  }

  it should "return value if block succeeds with retry" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    assertResult(2)(Await.result(f, waitAtMost))
  }

  it should "throw TooManyRetriesException (with cause) if block fails and no retry is allowed" in {
    val bar = mock[Bar]
    val maxRetry = 0

    def foo: Int = bar.inc(1)

    when(foo).thenThrow(new IllegalStateException)
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    val caught = intercept[TooManyRetriesException](Await.result(f, waitAtMost))
    assertResult(true) {
      caught.getCause match {
        case _: IllegalStateException => true
        case _ => false
      }
    }
  }

  it should "throw TooManyRetriesException (with cause) if all retries are used up" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo).thenThrow(new IllegalStateException)
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    val caught = intercept[TooManyRetriesException](Await.result(f, waitAtMost))
    assertResult(true) {
      caught.getCause match {
        case _: IllegalStateException => true
        case _ => false
      }
    }
  }

  "Number of Attempts" should "execute block exactly once if first attempt passes" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo).thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    // Do not use callback as it is non-blocking,
    // any exception thrown results in test pass.
    // Instead, block till future completes.
    Await.ready(f, waitAtMost)
    verify(bar, times(1)).inc(anyInt)
  }

  it should "execute block 2 times if first attempt fails" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    Await.ready(f, waitAtMost)
    verify(bar, times(maxRetry + 1)).inc(anyInt)
  }

  it should "execute block 3 times if earlier attempts fail" in {
    val bar = mock[Bar]
    val maxRetry = 2

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    Await.ready(f, waitAtMost)
    verify(bar, times(maxRetry + 1)).inc(anyInt)
  }

  it should "execute block exactly once if max #retries is zero" in {
    val bar = mock[Bar]
    val maxRetry = 0

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    Await.ready(f, waitAtMost)
    verify(bar, times(1)).inc(anyInt)
  }

  it should "be unlimited if max #retries is a negative number" in {
    val bar = mock[Bar]
    val maxRetry = -1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    Await.ready(f, waitAtMost)
    verify(bar, times(4)).inc(anyInt) // scalastyle:ignore
  }

  "Deadline" should "not be exceeded by retries" in {
    val bar = mock[Bar]
    val maxRetry = -1

    def deadline: Option[Deadline] = Option(400 millisecond fromNow)

    def foo: Int = bar.incWithDelay(1)

    when(foo).thenThrow(new RuntimeException)
    val f: Future[Int] = retry[Int](maxRetry, deadline)(foo)
    Await.ready(f, waitAtMost)
    verify(bar, atLeast(3)).incWithDelay(anyInt)
    verify(bar, atMost(4)).incWithDelay(anyInt) // scalastyle:ignore
  }

  it should "throw DeadlineExceededException (with cause) if exceeded" in {
    val bar = mock[Bar]
    val maxRetry = -1

    def deadline: Option[Deadline] = Option(50 millisecond fromNow)

    def foo: Int = bar.inc(1)

    when(foo).thenThrow(new IllegalStateException)
    val f: Future[Int] = retry[Int](maxRetry, deadline)(foo)
    val caught = intercept[DeadlineExceededException](Await.result(f, waitAtMost))
    assertResult(true) {
      caught.getCause match {
        case _: IllegalStateException => true
        case _ => false
      }
    }
  }

  "Give Up On Selected Exceptions" should "not retry if selected exception is seen in 1st attempt" in {
    val bar = mock[Bar]
    val maxRetry = 10

    def foo: Int = bar.inc(1)

    when(foo).thenThrow(new IllegalArgumentException)
    val f: Future[Int] = retry[Int](
      maxRetry,
      giveUpOnThrowable = giveUpOnIllegalArgumentException
    )(foo)
    Await.ready(f, waitAtMost)
    verify(bar, times(1)).inc(anyInt)
  }

  it should "retry until selected exception is seen" in {
    val bar = mock[Bar]
    val maxRetry = 10

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new IllegalStateException)
      .thenThrow(new IllegalArgumentException)
    val f: Future[Int] = retry[Int](
      maxRetry,
      giveUpOnThrowable = giveUpOnIllegalArgumentException
    )(foo)
    Await.ready(f, waitAtMost)
    verify(bar, times(2)).inc(anyInt)
  }

  it should "retry normally if selected exception is never seen" in {
    val bar = mock[Bar]
    val maxRetry = 2

    def foo: Int = bar.inc(1)

    when(foo).thenThrow(new IllegalStateException)

    val f: Future[Int] = retry[Int](
      maxRetry,
      giveUpOnThrowable = giveUpOnIllegalArgumentException
    )(foo)
    Await.ready(f, waitAtMost)
    verify(bar, times(3)).inc(anyInt)
  }
}
