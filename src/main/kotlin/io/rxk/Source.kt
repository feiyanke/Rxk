package io.rxk

import java.util.concurrent.*

//interface ISource<S> : ISignal<S> {
//    fun reset()
//    override fun makeContext(): Context<S, S> {
//        return super.makeContext().apply { reset.out { this@ISource.reset() } }
//    }
//}

abstract class Source<S> : Signal<S>() {

    abstract fun reset()
    override val reset = method { reset() }

    companion object {
        fun <S> create(block:Source<S>.()->Unit) : Context<S, S> {
            return object : Source<S>() {
                override fun reset() {
                    try {
                        block()
                    } catch (e:Throwable) {
                        error(e)
                        finish()
                    }
                }
            }.makeContext()
        }

        fun fromRunable(block:()->Unit):Context<Unit, Unit> {
            return object : Source<Unit>() {
                override fun reset() {
                    try {
                        block()
                    } catch (e:Throwable) {
                        error(e)
                    } finally {
                        finish()
                    }
                }
            }.makeContext()
        }

        fun from(runnable: Runnable):Context<Unit, Unit> {
            return fromRunable(runnable::run)
        }

        fun <T> fromCallable(callable:()->T) : Context<T, T> {
            return object : Source<T>() {
                override fun reset() {
                    try {
                        next(callable())
                    } catch (e:Throwable) {
                        error(e)
                    } finally {
                        finish()
                    }
                }
            }.makeContext()
        }

        fun <T> from(callable: Callable<T>):Context<T, T> {
            return fromCallable(callable::call)
        }

        fun <T> from(future: Future<T>):Context<T, T> {
            return fromCallable(future::get)
        }

        fun interval(ms: Long):Context<Int, Int> {
            return IntervalSource(ms).makeContext()
        }
    }
}

class IntervalSource(val ms:Long) : Source<Int>() {

    var count = 0

    override fun reset() {
        count = 0
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            next(count)
            count++
        }, 0, ms, TimeUnit.MILLISECONDS)
    }
}

//fun <T> Source<T>.asStream() : Stream<T> {
//    return SourceToStream(this)
//}
//
//class SourceTake<T>(source: Source<T>, val number: Int) : SourceOperator<T, T>(source) {
//    var count = 0
//
//    override fun start() {
//        super.start()
//        count = 0
//    }
//
//    override fun next(v: T) {
//        if (count < number) {
//            count++
//            doNext(v)
//        } else {
//            finish()
//        }
//    }
//
//    override fun error(e: Throwable) {
//        if (count < number) {
//            count++
//            doError(e)
//        } else {
//            finish()
//        }
//    }
//
//    override fun finish() {
//        doFinish()
//    }
//}
//

//
//
//
//fun <T> Source<T>.take(n: Int) : Source<T> {
//    return SourceTake(this, n)
//}
//
