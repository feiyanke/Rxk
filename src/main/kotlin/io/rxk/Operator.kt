package io.rxk

import java.util.concurrent.LinkedTransferQueue

abstract class ContextMethod<in T, R> {
    //    fun next(v:T) {
//        try {
//            invoke(v)
//        } catch (e:Throwable) {
//            error(e)
//        }
//    }
    abstract val next : IMethod<T, R>
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IEasyMethod<Unit>? = null
    open val request : IEasyMethod<Int>? = null
    open val reset : IEasyMethod<Unit>? = null
//    fun makeContext(ctx:IContext<*, E1> = EmptyContext()) : IContext<*, E2>
}

open class EasyContextMethod<R> {
    open val next: IEasyMethod<R>? = null
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IEasyMethod<Unit>? = null
    open val request : IEasyMethod<Int>? = null
    open val reset : IEasyMethod<Unit>? = null
}

class AsStream<S> : EasyContextMethod<S>() {
    private object finished
    private val queue : LinkedTransferQueue<Any> = LinkedTransferQueue()
    override val next = method<S> { queue.add(it) }
    override val error = method<Throwable> { queue.add(it) }
    override val finish = method { queue.add(finished) }
    override val reset = method { queue.clear();output() }
    override val request = method<Int> {
        for (i in 0 until it) {
            val a = queue.take()
            if (a is Throwable) {
                error(a)
            } else if (a == finished) {
                finish()
            } else {
                next(a as S)
            }
        }
        output(it)
    }
}


//
//abstract class Operator<T, R>(val stream: Stream<T>) : BaseStream<R>(), Receiver<T> {
//
//    init {
//        stream.receiver = this
//    }
//
//    fun doRequest(n:Int=1) {
//        stream.request(n)
//    }
//
//    fun doStart() {
//        stream.start()
//    }
//
//    override fun request(n: Int) {
//        doRequest(n)
//    }
//
//    override fun start() {
//        doStart()
//    }
//
//    override fun error(e: Throwable) {
//        doError(e)
//    }
//
//    override fun finish() {
//        doFinish()
//    }
//}
//
//class Map<T, R>(stream: Stream<T>, val transform:(T)->R) : Operator<T, R>(stream) {
//    override fun next(v: T) {
//        try {
//            doNext(transform(v))
//        } catch (e:Throwable) {
//            doError(e)
//        }
//    }
//}
//
//class Filter<T>(stream: Stream<T>, val predicate: (T)->Boolean):Operator<T,T>(stream) {
//    override fun next(v: T) {
//        try {
//            if (predicate(v)) doNext(v) else doRequest()
//        } catch (e:Throwable) {
//            doError(e)
//        }
//
//    }
//}
//
//class Take<T>(stream: Stream<T>, val number:Int) : Operator<T, T>(stream) {
//
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
//
//
//}
//
//class Scheduler<T>(stream: Stream<T>, val scheduler : Executor) : Operator<T, T>(stream) {
//
//    override fun next(v: T) {
//        scheduler.execute { doNext(v) }
//    }
//
//    override fun error(e: Throwable) {
//        scheduler.execute {
//            doError(e)
//            println("111111")
//        }
//    }
//
//    override fun finish() {
//        scheduler.execute {
//            doFinish()
//        }
//    }
//
//    override fun request(n: Int) {
//        stream.request(n)
//    }
//
//    override fun start() {
//        stream.start()
//    }
//}
//
//class ForEach<T>(stream: Stream<T>, val block: (T) -> Unit) : Operator<T, T>(stream) {
//
//    override fun next(v: T) {
//        try {
//            block(v)
//            stream.request()
//        } catch (e:Throwable) {
//            doError(e)
//        }
//    }
//}
//
//class IgnoreError<T>(stream: Stream<T>, val block: (e:Throwable) -> Unit) : Operator<T, T>(stream) {
//    override fun next(v: T) {
//        doNext(v)
//    }
//
//    override fun error(e: Throwable) {
//        try {
//            block(e)
//            stream.request()
//        } catch (e:Throwable) {
//            doError(e)
//        }
//    }
//}
//
//class Error<T>(stream: Stream<T>, val block: (e:Throwable) -> Unit) : Operator<T, T>(stream) {
//    override fun next(v: T) {
//        doNext(v)
//    }
//
//    override fun error(e: Throwable) {
//        try {
//            block(e)
//        } catch (e:Throwable) {
//            doError(e)
//        }
//    }
//}
//
//class RepeatStream<T>(stream: Stream<T>, val n:Int) : Operator<T, T>(stream) {
//    override fun next(v: T) {
//        doNext(v)
//    }
//
//    var count = 0
//    override fun start() {
//        super.start()
//        count = 0
//    }
//
//    override fun finish() {
//        if (count < n) {
//            request()
//        } else {
//            doFinish()
//        }
//    }
//}
//
////fun <T> Stream<T>.repeat(n:Int):Stream<T> {
////    return RepeatStream<T>(this, n)
////}
////
////fun <T> Stream<T>.filter(predicate: (T) -> Boolean) : Stream<T> {
////    return Filter(this, predicate)
////}
////
////fun <T, R> Stream<T>.map(transform: (T) -> R) : Stream<R> {
////    return Map(this, transform)
////}
////
////fun <T> Stream<T>.forEach(block:(T)->Unit) : Stream<T> {
////    return ForEach(this, block)
////}
////
////fun <T> Stream<T>.error(block: (e: Throwable) -> Unit) : Stream<T> {
////    return Error(this, block)
////}
////
////fun <T> Stream<T>.ignoreError(block: (e: Throwable) -> Unit) : Stream<T> {
////    return IgnoreError(this, block)
////}
////
////fun <T> Stream<T>.finish(block: () -> Unit = {}) {
////    receiver = Finish(block)
////    start()
////    request()
////}
////
////fun <T> Stream<T>.on(executor: Executor) : Stream<T> {
////    return Scheduler(this, executor)
////}
////
////fun <T> Stream<T>.take(count:Int) :Stream<T> {
////    return Take(this, count)
////}
////
////
////fun <T> Iterable<T>.asStream() = IterableStream(this)