package io.rxk

import java.util.concurrent.Callable
import java.util.concurrent.Future

interface IStream<T> : ISource<T> {
    fun request(n:Int)
    override fun makeContext(ctx: IContext<*, T>): IContext<*, T> {
        return super.makeContext(ctx).apply { request.out { this@IStream.request(it) } }
    }
}

abstract class Stream<T> : IStream<T> {
    override var output: (T) -> Unit = {}
    open val error : IEasyMethod<Throwable> = ErrorMethod()
    open val finish : IEasyMethod<Unit> = FinishMethod()

    companion object {
        fun empty() = EmptyStream()
        fun never() = NeverStream()
        fun throws(e:Throwable) = ThrowStream(e)
        fun <T> from(iterable: Iterable<T>) = IterableStream(iterable)
        fun <T> create(block:Source<T>.()->Unit) : IContext<*, T> = Source.create(block).asStream()
        fun fromRunable(block:()->Unit):Stream<Unit> {
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
            }
        }

        fun from(runnable: Runnable):Stream<Unit> {
            return fromRunable(runnable::run)
        }

        fun <T> fromCallable(callable:()->T) : Stream<T> {
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
            }
        }

        fun <T> from(callable: Callable<T>):Stream<T> {
            return fromCallable(callable::call)
        }

        fun <T> from(future: Future<T>):Stream<T> {
            return fromCallable(future::get)
        }

        fun interval(ms: Long):Stream<Int> = Source.interval(ms).asStream()

        fun <T> just(v:T) : Stream<T> {
            return fromCallable { v }
        }

        fun range(start:Int, count:Int) : Stream<Int> {
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


