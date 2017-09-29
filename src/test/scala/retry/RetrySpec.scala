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

  "retry" should "execute block only once if first attempt passes" in {
    val bar = mock[Bar]
    retry[Int](maxRetry = 1) {
      bar.inc(1)
    }
    verify(bar, times(1)).inc(_)
  }

  it should "execute block 2 times if first attempt fails" in {
    val bar = mock[Bar]
    when(bar.inc(1))
      .thenThrow(new RuntimeException)
      .thenReturn(2)
    retry[Int](maxRetry = 1) {
      bar.inc(1)
    }
    verify(bar, times(2)).inc(_)
  }

  it should "execute block 3 times if earlier attempts fail" in {
    val bar = mock[Bar]
    when(bar.inc(1))
      .thenThrow(new RuntimeException)
      .thenThrow(new RuntimeException)
      .thenReturn(2)
    retry[Int](maxRetry = 2) {
      bar.inc(1)
    }
    verify(bar, times(3)).inc(_)
  }
}
