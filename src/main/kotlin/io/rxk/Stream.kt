package io.rxk

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

abstract class Stream<R> {
    open protected val signal: IEasyMethod<R> = empty<R>()
    open protected val error : IEasyMethod<Throwable> = empty<Throwable>()
    open protected val finish : IUnitMethod = empty()
    open protected val start : IUnitMethod = empty()
    open protected val cancel : IUnitMethod = empty()
    open protected val report : IUnitMethod = empty()
    fun make():Context<R, R> = Context(signal, error, finish, start, cancel, report)
}

abstract class BaseStream<R>:Stream<R>(){

    private var count = AtomicInteger(0)

    override val signal = method<R> {
        count.incrementAndGet()
        output(it)
    }

    override val error = empty<Throwable>()

    override val finish = method {
        if (count.decrementAndGet()==-1) {
            output()
        }
    }

    override val cancel = method {
        signal.output = {}
        error.output = {}
        finish.output = {}
    }

    override val report = method {
        if (count.decrementAndGet()==-1) {
            finish.output()
        }
    }
}

class BlockStream<R>(block:BaseStream<R>.()->Unit) : BaseStream<R>() {
    override val start = method {
        try {
            block()
        } catch (e:Throwable) {
            error(e)
        } finally {
            finish()
        }
    }
}

class RunableStream(block:()->Unit) : BaseStream<Unit>() {
    override val start = method {
        try {
            block()
        } catch (e:Throwable) {
            error(e)
        } finally {
            finish()
        }
    }
}

class CallableStream<R>(callable:()->R) : BaseStream<R>() {
    override val start = method {
        try {
            signal(callable())
        } catch (e:Throwable) {
            error(e)
        } finally {
            finish()
        }
    }
}

class IntervalStream(ms:Long):BaseStream<Int>(){
    var count = AtomicInteger(0)
    override val start = method {
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            signal(count.getAndIncrement())
        },0, ms, TimeUnit.MILLISECONDS)
    }
}

class IterableStream<T>(iterable: Iterable<T>):BaseStream<T>() {
    val iter = iterable.iterator()
    override val start = method {
        iterable.forEach { signal(it) }
        finish()
    }
}


class MergeStream<T>(list:List<Context<*, T>>):BaseStream<T>() {
    private val count = AtomicInteger(list.size)
    override val start = method {
        list.forEach {
            thread {
                it.forEach {
                    signal(it)
                }.finish {
                    if (count.decrementAndGet()==0){
                        finish()
                    }
                }.start()
            }
        }
    }
}