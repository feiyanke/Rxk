package io.rxk

import java.util.concurrent.*

interface ISource<S> : ISignal<S> {
    fun reset()
    override fun makeContext(ctx: IContext<*, S>): IContext<*, S> {
        return super.makeContext(ctx).apply { reset.out { this@ISource.reset() } }
    }
}

abstract class Source<S> : ISource<S> {

    override var output: (S) -> Unit = {}

    val error = ErrorMethod()
    val finish = FinishMethod()

    override fun makeContext(ctx: IContext<*, S>): IContext<*, S> {
        return super.makeContext(ctx).make(error = error, finish = finish)
    }

    companion object {
        fun <T> create(block:Source<T>.()->Unit) : IContext<*, T> {
            return object : Source<T>() {
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

        fun fromRunable(block:()->Unit):ISource<Unit> {
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
            }
        }

        fun from(runnable: Runnable):ISource<Unit> {
            return fromRunable(runnable::run)
        }

        fun <T> fromCallable(callable:()->T) : ISource<T> {
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
            }
        }

        fun <T> from(callable: Callable<T>):ISource<T> {
            return fromCallable(callable::call)
        }

        fun <T> from(future: Future<T>):ISource<T> {
            return fromCallable(future::get)
        }

        fun interval(ms: Long):IContext<*, Int> {
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
