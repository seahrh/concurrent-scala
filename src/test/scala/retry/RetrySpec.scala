package retry

import Retry._
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.FlatSpec
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{DurationLong, fromNow}
import scala.language.postfixOps

class RetrySpec extends FlatSpec with MockitoSugar {

  class Bar {
    def inc(i: Int): Int = i + 1

    def incWithDelay(i: Int): Int = {
      Thread.sleep(100) // scalastyle:ignore
      inc(i)
    }
  }

  "Future" should "return value if block succeeds without retry" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo).thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry) {
      foo
    }
    assertResult(2)(Await.result(f, 10 second))
  }

  it should "return value if block succeeds with retry" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(bar.inc(1))
      .thenThrow(new IllegalStateException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry) {
      bar.inc(1)
    }
    assertResult(2)(Await.result(f, 10 second))
  }

  it should "throw the last encountered exception if block fails without retry" in {
    val bar = mock[Bar]
    val maxRetry = 0

    def foo: Int = bar.inc(1)

    when(foo).thenThrow(new RuntimeException)
    val f: Future[Int] = retry[Int](maxRetry) {
      foo
    }
    assertThrows[RuntimeException](Await.result(f, 10 second))
  }

  it should "throw TooManyRetriesException if block fails with retry" in {
    pending
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
    val f: Future[Int] = retry[Int](maxRetry) {
      foo
    }
    assertThrows[TooManyRetriesException](Await.result(f, 10 second))
  }

  "Number of Attempts" should "execute block exactly once if first attempt passes" in {
    pending
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo).thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry) {
      bar.inc(1)
    }
    // Do not use callback as it is non-blocking,
    // any exception thrown results in test pass.
    // Instead, block till future completes.
    Await.ready(f, 10 second)
    verify(bar, times(1)).inc(1)
  }

  it should "execute block 2 times if first attempt fails" in {
    pending
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenAnswer(throw new RuntimeException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry) {
      foo
    }
    Await.ready(f, 10 second)
    verify(bar, times(maxRetry + 1)).inc(1)
  }

  it should "execute block 3 times if earlier attempts fail" in {
    pending
    val bar = mock[Bar]
    val maxRetry = 2

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    retry[Int](maxRetry) {
      foo
    }
    verify(bar, times(maxRetry + 1)).inc(_)
  }

  it should "not exceed the maximum number of retries" in {
    pending
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    retry[Int](maxRetry) {
      foo
    }
    verify(bar, times(maxRetry + 1)).inc(_)
  }

  it should "execute block exactly once if maximum number of retries is zero" in {
    pending
    val bar = mock[Bar]
    val maxRetry = 0

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    retry[Int](maxRetry) {
      foo
    }
    verify(bar, times(1)).inc(_)
  }

  it should "be unlimited if maximum number of retries is a negative number" in {
    pending
    val bar = mock[Bar]
    val maxRetry = -1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    retry[Int](maxRetry) {
      foo
    }
    verify(bar, times(4)).inc(_) // scalastyle:ignore
  }

  "Deadline" should "not be exceeded by retries" in {
    pending
    val bar = mock[Bar]
    val maxRetry = 3

    def foo: Int = bar.incWithDelay(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    retry[Int](maxRetry, deadline = Option(1300 millisecond fromNow)) {
      foo
    }
    verify(bar, times(4)).incWithDelay(_) // scalastyle:ignore
  }
}
