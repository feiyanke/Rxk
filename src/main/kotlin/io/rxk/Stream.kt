package io.rxk

import java.util.concurrent.Callable
import java.util.concurrent.Future

//abstract open class Stream<T> : Source<T> {
//    abstract fun request(n:Int)
//    override fun makeContext(ctx: IContext<*, T>): IContext<*, T> {
//        return super.makeContext(ctx).apply { request.out { this@IStream.request(it) } }
//    }
//}

class StreamContext<T, R> (
        next : IMethod<T, R>,
        error : IEasyMethod<Throwable>,
        finish : IEasyMethod<Unit>,
        reset : IEasyMethod<Unit>,
        var request: IEasyMethod<Int>)
    : SourceContext<T, R>(next, error, finish, reset) {

    override fun <E> make(next : IMethod<R, E>,
                          error : IEasyMethod<Throwable>?,
                          finish : IEasyMethod<Unit>?,
                          request : IEasyMethod<Int>?,
                          reset : IEasyMethod<Unit>?
    ) : StreamContext<T, E> = chainNext(next).apply {
        chainError(error)
        chainFinish(finish)
        chainReset(reset)
        chainRequest(request)
    }

    override fun make(next : IEasyMethod<R>?,
                      error : IEasyMethod<Throwable>?,
                      finish : IEasyMethod<Unit>?,
                      request : IEasyMethod<Int>?,
                      reset : IEasyMethod<Unit>?
    ) : StreamContext<T, R> = apply {
        chainNext(next)
        chainError(error)
        chainFinish(finish)
        chainReset(reset)
    }

    private fun <E> chainNext(m:IMethod<R, E>) : StreamContext<T, E>
            = StreamContext(next.chain(m), error, finish, reset, request)

    protected fun chainRequest(m:IEasyMethod<Int>?) {
        if (m!=null)request = m.chain(request)
    }

}

abstract class Stream<T> : Source<T>() {

    abstract fun request(n:Int)
    val request = method<Int> { request(it) }
    override fun makeContext(): StreamContext<T, T> = StreamContext(this, error, finish, reset, request)

    companion object {
        fun empty() = EmptyStream().makeContext()
        fun never() = NeverStream().makeContext()
        fun throws(e:Throwable) = ThrowStream(e).makeContext()
        fun <T> from(iterable: Iterable<T>) = IterableStream(iterable).makeContext()
        fun <T> create(block:Source<T>.()->Unit) : StreamContext<T, T> = Source.create(block).asStream()
        fun fromRunable(block:()->Unit):StreamContext<Unit, Unit> {
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

        fun from(runnable: Runnable):StreamContext<Unit, Unit> {
            return fromRunable(runnable::run)
        }

        fun <T> fromCallable(callable:()->T) : StreamContext<T, T> {
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

        fun <T> from(callable: Callable<T>):StreamContext<T, T> {
            return fromCallable(callable::call)
        }

        fun <T> from(future: Future<T>):StreamContext<T, T> {
            return fromCallable(future::get)
        }

        fun interval(ms: Long):StreamContext<Int, Int> = Source.interval(ms).asStream()

        fun <T> just(v:T) : StreamContext<T, T> {
            return fromCallable { v }
        }

        fun range(start:Int, count:Int) : StreamContext<Int, Int> {
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


