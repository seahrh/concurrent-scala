package retry

import Retry._
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.FlatSpec
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class RetrySpec extends FlatSpec with MockitoSugar {

  class Bar {
    def inc(i: Int): Int = i + 1
  }

  "Future" should "return value if block succeeds without retry" in {
    val bar = mock[Bar]
    val maxRetry = 1
    def foo: Int = bar.inc(1)
    when(foo).thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry) {
      bar.inc(1)
    }
    for (a <- f) {
      assertResult(2)(a)
    }
  }

  it should "return value if block succeeds with retry" in {
    val bar = mock[Bar]
    val maxRetry = 1
    def foo: Int = bar.inc(1)
    when(foo)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    val f: Future[Int] = retry[Int](maxRetry) {
      bar.inc(1)
    }
    for (a <- f) {
      assertResult(2)(a)
    }
  }

  it should "throw exception if block fails without retry" in {
    val bar = mock[Bar]
    val maxRetry = 0
    def foo: Int = bar.inc(1)
    when(foo).thenThrow(new RuntimeException)
    val f: Future[Int] = retry[Int](maxRetry) {
      bar.inc(1)
    }
    for (a <- f) {
      assertThrows[RuntimeException](a)
    }
  }

  it should "throw exception if block fails with retry" in {
    val bar = mock[Bar]
    val maxRetry = 1
    def foo: Int = bar.inc(1)
    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
    val f: Future[Int] = retry[Int](maxRetry) {
      bar.inc(1)
    }
    for (a <- f) {
      assertThrows[RuntimeException](a)
    }
  }

  "Number of Attempts" should "execute block exactly once if first attempt passes" in {
    val bar = mock[Bar]
    val maxRetry = 1
    def foo: Int = bar.inc(1)
    when(foo).thenCallRealMethod()
    retry[Int](maxRetry) {
      bar.inc(1)
    }
    verify(bar, times(1)).inc(_)
  }

  it should "execute block 2 times if first attempt fails" in {
    val bar = mock[Bar]
    val maxRetry = 1
    def foo: Int = bar.inc(1)
    when(foo)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    retry[Int](maxRetry) {
      foo
    }
    verify(bar, times(maxRetry + 1)).inc(_)
  }

  it should "execute block 3 times if earlier attempts fail" in {
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
    verify(bar, times(5)).inc(_) // scalastyle:ignore
  }
}
