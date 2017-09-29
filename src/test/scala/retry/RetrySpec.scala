package retry

import Retry._
import org.mockito.Mockito.{times, verify, when}
import org.scalatest.FlatSpec
import org.scalatest.mockito.MockitoSugar

import scala.concurrent.ExecutionContext.Implicits.global

class RetrySpec extends FlatSpec with MockitoSugar {

  class Bar {
    def inc(i: Int): Int = i + 1
  }

  "number of attempts" should "execute block only once if first attempt passes" in {
    val bar = mock[Bar]
    retry[Int](maxRetry = 1) {
      bar.inc(1)
    }
    verify(bar, times(1)).inc(_)
  }

  it should "execute block 2 times if first attempt fails" in {
    val bar = mock[Bar]
    def foo: Int = bar.inc(1)
    when(foo)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    retry[Int](maxRetry = 1) {
      foo
    }
    verify(bar, times(2)).inc(_)
  }

  it should "execute block 3 times if earlier attempts fail" in {
    val bar = mock[Bar]
    def foo: Int = bar.inc(1)
    when(foo)
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenCallRealMethod()
    retry[Int](maxRetry = 2) {
      foo
    }
    verify(bar, times(3)).inc(_)
  }
}
