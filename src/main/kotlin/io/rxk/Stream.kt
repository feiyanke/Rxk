package io.rxk

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

abstract class Stream<R> {
    open val next: IEasyMethod<R> = empty<R>()
    open val error : IEasyMethod<Throwable> = empty<Throwable>()
    open val finish : IUnitMethod = empty()
    open val start : IUnitMethod = empty()
    open val cancel : IUnitMethod = empty()
    open val report : IUnitMethod = empty()
}

class BlockStream<R>(block:Stream<R>.()->Unit) : Stream<R>() {
    override val start = method {
        try {
            block()
        } catch (e:Throwable) {
            error(e)
            finish()
        }
    }
    override val cancel = method {
        next.output = {}
    }
}

class RunableStream(block:()->Unit) : Stream<Unit>() {
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

class CallableStream<R>(callable:()->R) : Stream<R>() {
    override val start = method {
        try {
            next(callable())
        } catch (e:Throwable) {
            error(e)
        } finally {
            finish()
        }
    }
}

class IntervalStream(ms:Long):Stream<Int>(){
    var count = 0
    override val start = method {
        count = 0
        Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
            next(count)
            count++
        },0, ms, TimeUnit.MILLISECONDS)
    }
}



