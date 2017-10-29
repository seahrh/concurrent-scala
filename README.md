# Retry in Scala
Retry a code block until it stops throwing exception.

Highly Configurable
- Set a maximum number of retries
- Set a deadline with `scala.concurrent.duration.Deadline`
- Set a backoff duration after which to retry
- Determine which exception to stop retry

Uses `scala.concurrent`, with no other dependencies. Based on [Mortimerp9's gist](https://gist.github.com/Mortimerp9/5430595). Added unit tests and backoff options. Refactoring and removed lint warts.
## Code Examples
You *must*
- Provide an [`ExecutionContext`](https://docs.scala-lang.org/overviews/core/futures.html#execution-context) before calling `Retry`. The simplest way is to `import scala.concurrent.ExecutionContext.Implicits.global` 
- Set the maximum number of retries, given in the `maxRetry` argument. A negative number means an unlimited number of retries.
### You don't care about the result
```scala
retry[Unit](maxRetry = 10){
  send(email) // return type is Unit
}
```
### You care about the result
```scala
import scala.concurrent.Future
val f: Future[Double] = retry[Double](maxRetry = 10){
  api.get("stock/price/goog")
}
```
### Keep trying until a deadline has elapsed
`deadline` is an optional argument of type `Option[Deadline]`. Default value is `None`.
```scala
import scala.concurrent.duration.{Deadline, DurationInt, fromNow}
retry[Unit](
  maxRetry = 10,
  deadline = Option(2 hour fromNow)
){
  send(email)
}
```
### Customize the backoff function
The default backoff function is unbounded and grows exponentially with the number of retries, in 100 millisecond steps. Alternatively, you can define your own backoff function and pass it in the `backoff` argument.

The backoff function takes a `retryCnt` argument of type `Int` and returns a `scala.concurrent.duration.Duration`. It will retry after this duration has passed. 
```scala
import scala.concurrent.duration.{Duration, DurationInt}
def atMostOneDay(retryCnt: Int): Duration = {
    val max: Duration = 24 hours
    val d: Duration = Retry.exponentialBackoff(retryCnt)
    if (d < max) d else max
}
retry[Unit](
  maxRetry = 10,
  backoff = atMostOneDay
){
  send(email)
}
```
