package retry

import Retry._
import org.mockito.Mockito.{times, verify, when}
import org.mockito.ArgumentMatchers.anyInt
import org.scalatest.FlatSpec
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}
import scala.concurrent.duration.{Duration, DurationLong, fromNow}
import scala.language.postfixOps

class RetrySpec extends FlatSpec with MockitoSugar {
  private val atMost: Duration = 4 second

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
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    assertResult(2)(Await.result(f, atMost))
  }

  it should "return value if block succeeds with retry" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new IllegalStateException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    assertResult(2)(Await.result(f, atMost))
  }

  it should "throw the last encountered exception if block fails without retry" in {
    val bar = mock[Bar]
    val maxRetry = 0

    def foo: Int = bar.inc(1)

    when(foo).thenThrow(new RuntimeException)
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    assertThrows[RuntimeException](Await.result(f, atMost))
  }

  it should "throw the last encountered exception if block fails with retry" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo).thenThrow(new RuntimeException)
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    assertThrows[RuntimeException](Await.result(f, atMost))
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
    Await.ready(f, atMost)
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
    Await.ready(f, atMost)
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
    Await.ready(f, atMost)
    verify(bar, times(maxRetry + 1)).inc(anyInt)
  }

  it should "not exceed max #retries and return last encountered exception" in {
    val bar = mock[Bar]
    val maxRetry = 1

    def foo: Int = bar.inc(1)

    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry)(foo)
    assertThrows[RuntimeException](Await.result(f, atMost))
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
    Await.ready(f, atMost)
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
    Await.ready(f, atMost)
    verify(bar, times(4)).inc(anyInt) // scalastyle:ignore
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
