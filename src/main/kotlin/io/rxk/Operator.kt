package io.rxk

import java.util.concurrent.LinkedTransferQueue

abstract class Operator<in T, R> {
    abstract val next : IMethod<T, R>
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IEasyMethod<Unit>? = null
    open val request : IEasyMethod<Int>? = null
    open val reset : IEasyMethod<Unit>? = null
}

abstract class EasyOperator<R> {
    open val next: IEasyMethod<R>? = null
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IEasyMethod<Unit>? = null
    open val request : IEasyMethod<Int>? = null
    open val reset : IEasyMethod<Unit>? = null
}

abstract class SignalToSourceOperator<R> {
    open val next: IEasyMethod<R>? = null
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IEasyMethod<Unit>? = null
    abstract val reset : IEasyMethod<Unit>
}


abstract class SourceToStreamOperator<R> {
    open val next: IEasyMethod<R>? = null
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IEasyMethod<Unit>? = null
    open val reset : IEasyMethod<Unit>? = null
    abstract val requset : IEasyMethod<Unit>
}

abstract class SignalToStreamOperator<R> {
    open val next: IEasyMethod<R>? = null
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IEasyMethod<Unit>? = null
    abstract val reset : IEasyMethod<Unit>
    abstract val request : IEasyMethod<Int>
}

class SignalToStream<S> : SignalToStreamOperator<S>() {
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

class FilterOperator<T>(predicate: (T) -> Boolean) : EasyOperator<T>() {
    override val error = EmptyMethod<Throwable>()
    override val request = EmptyMethod<Int>()
    override val next = object : EasyMethod<T>() {
        override fun invoke(p1: T) {
            try {
                if (predicate(p1)) output(p1)
                else request(1)
            } catch (e : Throwable) {
                error(e)
            }
        }
    }
}


class MapOperator<in R, E>(transform: (R) -> E) : Operator<R, E>() {

    override val error = EmptyMethod<Throwable>()

    override val next = object : Method<R, E>() {
        override fun invoke(p1: R) {
            try {
                output(transform(p1))
            } catch (e : Throwable) {
                error(e)
            }
        }
    }
}

class ForEachOperator<T>(count:Int = 0, block:(T)->Unit):EasyOperator<T>() {
    override val error = EmptyMethod<Throwable>()
    override val request = EmptyMethod<Int>()
    override val next = method<T> {
        try {
            block(it)
            request(count)
        } catch (e:Throwable) {
            error(e)
        }
    }
}


//fun <R> Context<*, R>.forEach(n:Int = 1, block:(R)->Unit):Context<*, R> {
//    val error = EmptyMethod<Throwable>()
//    next.out {
//        try {
//            block(it)
//            request(1)
//        } catch (e:Throwable) {
//            error(e)
//        }
//    }
//    return chainError(error)
//}
//
//fun Context<*, *>.finish(block: () -> Unit) : Context<*, *> {
//    finish.out {
//        block()
//    }
//    return this
//}
//
//fun Context<*, *>.error(block: (e:Throwable) -> Unit) : Context<*, *> {
//    error.out {
//        block(it)
//    }
//    return this
//}
//
//fun Context<*, *>.start() {
//    reset(Unit)
//    request(1)
//}
//
//fun main(args: Array<String>) {
//    Context.from(0..100)
//            .map { it*it }
//            .filter { it % 2 == 0 }
//            .map { it.toString() }
//            //.filter { it.length < 3 }
//            .forEach { println(it) }
//            .finish {
//                println("finished")
//            }
//            .error {println("error : " + it.printStackTrace())}
//            .start()
//}


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