package io.rxk

import java.util.concurrent.*

interface ISource<T> : ISignal<T> {
    fun reset()
}

abstract class Source<T> : ISource<T> {

    override val context: Context<T, T> = EmptyContext<T>().apply{
        reset.out { this@Source.reset() }
    }

    companion object {
        fun <T> create(block:Source<T>.()->Unit) : Source<T> {
            return object : BaseSource<T>() {
                override fun start() {
                    try {
                        block()
                    } catch (e:Throwable) {
                        doError(e)
                        doFinish()
                    }

                }

            }
        }

        fun fromRunable(block:()->Unit):Source<Unit> {
            return object : BaseSource<Unit>() {
                override fun start() {
                    try {
                        block()
                    } catch (e:Throwable) {
                        doError(e)
                        doFinish()
                    }
                }
            }
        }

        fun from(runnable: Runnable):Source<Unit> {
            return fromRunable(runnable::run)
        }

        fun <T> fromCallable(callable:()->T) : Source<T> {
            return object : BaseSource<T>() {
                override fun start() {
                    try {
                        doNext(callable())
                    } catch (e:Throwable) {
                        doError(e)
                        doFinish()
                    }
                }
            }
        }

        fun <T> from(callable: Callable<T>):Source<T> {
            return fromCallable(callable::call)
        }

        fun <T> from(future: Future<T>):Source<T> {
            return fromCallable(future::get)
        }

        fun interval(ms: Long):Source<Int> {
            return IntervalSource(ms)
        }
    }
}

abstract class BaseSource<T>() : Source<T> {
    override var receiver : Receiver<T>? = null
}

abstract class SourceOperator<T, R>(val source: Source<T>) : BaseSource<R>(), Receiver<T> {

    init {
        source.receiver = this
    }

    fun doStart() {
        source.start()
    }

    override fun start() {
        doStart()
    }

    override fun error(e: Throwable) {
        doError(e)
    }

    override fun finish() {
        doFinish()
    }
}

class SourceTake<T>(source: Source<T>, val number: Int) : SourceOperator<T, T>(source) {
    var count = 0

    override fun start() {
        super.start()
        count = 0
    }

    override fun next(v: T) {
        if (count < number) {
            count++
            doNext(v)
        } else {
            finish()
        }
    }

    override fun error(e: Throwable) {
        if (count < number) {
            count++
            doError(e)
        } else {
            finish()
        }
    }

    override fun finish() {
        doFinish()
    }
}

class SourceToStream<T>(val source: Source<T>) : BaseStream<T>(), Receiver<T> {

    val queue : LinkedTransferQueue<Any> = LinkedTransferQueue()
    object finished
    init {
        source.receiver = this
    }

    fun doStart() {
        source.start()
    }

    override fun start() {
        queue.clear()
        doStart()
    }

    override fun next(v: T) {
        queue.add(v)
    }

    override fun error(e: Throwable) {
        queue.add(e)
    }

    override fun finish() {
        queue.add(finished)
    }

    override fun request(n: Int) {
        for (i in 0 until n) {
            requestOne()
        }
    }

    private fun requestOne() {
        val a = queue.take()
        if (a is Throwable) {
            doError(a)
        } else if (a == finished) {
            doFinish()
        } else {
            doNext(a as T)
        }
    }
}

fun <T> Source<T>.asStream() : Stream<T> {
    return SourceToStream(this)
}

fun <T> Source<T>.take(n: Int) : Source<T> {
    return SourceTake(this, n)
}

class IntervalSource(val ms:Long) : BaseSource<Int>() {

    var count = 0

    override fun start() {
        count = 0
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            doNext(count)
            count++
        }, 0, ms, TimeUnit.MILLISECONDS)
    }
}