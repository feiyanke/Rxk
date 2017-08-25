package io.rxk

import java.util.concurrent.Callable
import java.util.concurrent.Future

interface Stream<T> {
    var receiver : Receiver<T>?
    fun start()
    fun request(n:Int = 1)

    companion object {
        fun empty() = EmptyStream()
        fun never() = NeverStream()
        fun throws(e:Throwable) = ThrowStream(e)
        fun <T> from(iterable: Iterable<T>) = IterableStream(iterable)
        fun <T> create(block:Source<T>.()->Unit) : Stream<T> = Source.create(block).asStream()
        fun fromRunable(block:()->Unit):Stream<Unit> {
            return object : BaseStream<Unit>() {
                override fun request(n: Int) {
                    try {
                        block()
                    } catch (e:Throwable) {
                        doError(e)
                        doFinish()
                    }
                }

                override fun start() {}
            }
        }

        fun from(runnable: Runnable):Stream<Unit> {
            return fromRunable(runnable::run)
        }

        fun <T> fromCallable(callable:()->T) : Stream<T> {
            return object : BaseStream<T>() {
                override fun request(n: Int) {
                    try {
                        doNext(callable())
                    } catch (e:Throwable) {
                        doError(e)
                        doFinish()
                    }
                }

                override fun start() {}
            }
        }

        fun <T> from(callable: Callable<T>):Stream<T> {
            return fromCallable(callable::call)
        }

        fun <T> from(future: Future<T>):Stream<T> {
            return fromCallable(future::get)
        }
    }
}

abstract class BaseStream<T> : Stream<T> {
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
}


class IterableStream<T>(var iterable: Iterable<T>) : BaseStream<T>() {
    var iter : Iterator<T> = iterable.iterator()
    override fun request(n: Int) {
        for (i in 0..(n-1)) {
            try {
                if (iter.hasNext()) {
                    doNext(iter.next())
                } else {
                    doFinish()
                }
            } catch (e:Throwable) {
                doError(e)
            }
        }
    }

    override fun start() {
        iter = iterable.iterator()
    }
}

class EmptyStream : BaseStream<Unit>() {
    override fun start() {}

    override fun request(n: Int) {
        doFinish()
    }
}

class NeverStream : BaseStream<Unit>() {
    override fun start() {}

    override fun request(n: Int) {}
}

class ThrowStream(val e:Throwable) : BaseStream<Unit>() {
    override fun start() {}

    override fun request(n: Int) {
        doError(e)
        doFinish()
    }
}

