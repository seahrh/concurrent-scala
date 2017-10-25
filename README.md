# Retry in Scala
Retry a code block until it stops throwing exception.

Highly Configurable
- Set a maximum number of retries
- Set a deadline with `scala.concurrent.duration.Deadline`
- Set a backoff duration after which to retry
- Determine which exception to stop retry

Using `scala.concurrent`, with no other dependencies. Based on [Mortimerp9's gist](https://gist.github.com/Mortimerp9/5430595). Added unit tests and backoff options. Refactoring and removed lint warts.

## Code Examples

Basic use case: sending email over an unreliable network. 
```scala
retry[Unit](maxRetry = 10){
  send(email)
}
```
The `retry` method is of type `Unit` because it does not return any value. You must set the maximum number of retries, given in the `maxRetry` argument.
