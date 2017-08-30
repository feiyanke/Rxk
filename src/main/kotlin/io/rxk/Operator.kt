package io.rxk

import java.util.concurrent.Executor
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

class FinishOperator<T>(block: () -> Unit):EasyOperator<T>() {
    override val finish = method{block()}
}

class ErrorOperator<T>(block: (Throwable) -> Unit):EasyOperator<T>() {
    override val error = method<Throwable>{block(it)}
}



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



class TakeOperator<T>(val number:Int) : EasyOperator<T>() {

    var count = 0

    override val finish = EmptyUnitMethod()

    override val reset = method {
        count = 0
        output()
    }

    override val next = method<T> {
        if (count < number) {
            count++
            output(it)
        } else {
            finish()
        }
    }

    override val error = method<Throwable> {
        if (count < number) {
            count++
            output(it)
        } else {
            finish()
        }
    }
}

class ScheduleOperator<T>(val scheduler : Executor) : EasyOperator<T>() {
    override val next = method<T> {
        scheduler.execute { output(it) }
    }

    override val error = method<Throwable> {
        scheduler.execute { output(it) }
    }

    override val finish = method {
        scheduler.execute { output() }
    }
}
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