package io.rxk

import java.util.concurrent.Callable
import java.util.concurrent.Future

//abstract open class Stream<T> : Source<T> {
//    abstract fun request(n:Int)
//    override fun makeContext(ctx: IContext<*, T>): IContext<*, T> {
//        return super.makeContext(ctx).apply { request.out { this@IStream.request(it) } }
//    }
//}

abstract class Stream<T> : Source<T>() {

    abstract fun request(n:Int)
    override val request = method<Int> { request(it) }

    companion object {
        fun empty() = EmptyStream().makeContext()
        fun never() = NeverStream().makeContext()
        fun throws(e:Throwable) = ThrowStream(e).makeContext()
        fun <T> from(iterable: Iterable<T>) = IterableStream(iterable).makeContext()
        fun <T> create(block:Source<T>.()->Unit) : Context<T, T> = Source.create(block).asStream()
        fun fromRunable(block:()->Unit):Context<Unit, Unit> {
            return object : Stream<Unit>() {
                override fun request(n: Int) {
                    try {
                        block()
                    } catch (e:Throwable) {
                        error(e)
                    } finally {
                        finish()
                    }
                }

                override fun reset() {}
            }.makeContext()
        }

        fun from(runnable: Runnable):Context<Unit, Unit> {
            return fromRunable(runnable::run)
        }

        fun <T> fromCallable(callable:()->T) : Context<T, T> {
            return object : Stream<T>() {
                override fun request(n: Int) {
                    try {
                        next(callable())
                    } catch (e:Throwable) {
                        error(e)
                    } finally {
                        finish()
                    }
                }

                override fun reset() {}
            }.makeContext()
        }

        fun <T> from(callable: Callable<T>):Context<T, T> {
            return fromCallable(callable::call)
        }

        fun <T> from(future: Future<T>):Context<T, T> {
            return fromCallable(future::get)
        }

        fun interval(ms: Long):Context<Int, Int> = Source.interval(ms).asStream()

        fun <T> just(v:T) : Context<T, T> {
            return fromCallable { v }
        }

        fun range(start:Int, count:Int) : Context<Int, Int> {
            return from(start until start+count)
        }
    }
}

/*abstract class BaseStream<T> : Stream<T> {
    override var receiver: Receiver<T>? = null

    fun doNext(v:T) {
        receiver?.next(v)
    }

    fun doError(e:Throwable) {
        receiver?.error(e)
    }

    fun doFinish() {
        receiver?.finish()
    }
}*/


class IterableStream<T>(var iterable: Iterable<T>) : Stream<T>() {
    var iter : Iterator<T> = iterable.iterator()
    override fun request(n: Int) {
        for (i in 0..(n-1)) {
            try {
                if (iter.hasNext()) {
                    next(iter.next())
                } else {
                    finish()
                }
            } catch (e:Throwable) {
                error(e)
            }
        }
    }

    override fun reset() {
        iter = iterable.iterator()
    }
}

class EmptyStream : Stream<Unit>() {
    override fun reset() {}

    override fun request(n: Int) {
        finish()
    }
}

class NeverStream : Stream<Unit>() {
    override fun reset() {}

    override fun request(n: Int) {}
}

class ThrowStream(val e:Throwable) : Stream<Unit>() {
    override fun reset() {}

    override fun request(n: Int) {
        error(e)
        finish()
    }
}


