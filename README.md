# Retry in Scala
Retry a code block until it stops throwing exception.

Highly Configurable
- Set a maximum number of retries
- Set a deadline with `scala.concurrent.duration.Deadline`
- Set a backoff duration after which to retry
- Determine which exception to stop retry

Using `scala.concurrent`, with no other dependencies. Based on [Mortimerp9's gist](https://gist.github.com/Mortimerp9/5430595). Added unit tests and backoff options. Refactoring and removed lint warts.

## Code Examples

### You don't care about the result
```scala
retry[Unit](maxRetry = 10){
  send(email) // return type is Unit
}
```
You *must* set the maximum number of retries, given in the `maxRetry` argument.

### You care about the result
```scala
import scala.concurrent.Future
val f: Future[Double] = retry[Double](maxRetry = 10){
  api.get("stock/price/goog")
}
```

### Stop retry after a time period has elapsed
`deadline` is an optional argument of type `Option[Deadline]`. Default value is `None`.
```scala
import scala.concurrent.duration.{Deadline, DurationInt, fromNow}
retry[Unit](
  maxRetry = 10,
  deadline = Option(2 hour fromNow)
)(expensiveOperation)
```
