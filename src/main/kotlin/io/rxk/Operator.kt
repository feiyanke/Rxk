package io.rxk

import java.util.concurrent.Executor
import java.util.concurrent.Future
import java.util.concurrent.LinkedTransferQueue
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

abstract class Operator<in T, R> {
    abstract val signal: IMethod<T, R>
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IUnitMethod? = null

    open val start : IUnitMethod? = null
    open val cancel : IUnitMethod? = null
    open val report : IUnitMethod? = null
}

abstract class EasyOperator<R> {

    open val signal: IEasyMethod<R>? = null
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IUnitMethod? = null

    open val start : IUnitMethod? = null
    open val cancel : IUnitMethod? = null
    open val report : IUnitMethod? = null
}

class FilterOperator<T>(predicate: (T) -> Boolean) : EasyOperator<T>() {
    override val error = empty<Throwable>()
    override val report = empty()
    override val signal = object : EasyMethod<T>() {
        override fun invoke(p1: T) {
            try {
                if (predicate(p1)) output(p1)
                else report()
            } catch (e : Throwable) {
                report()
                error(e)
            }
        }
    }
}

class MapCallbackOperator<in R, E>(callback:(R, (E)->Unit)->Unit):Operator<R, E>(){
    override val error = empty<Throwable>()
    override val report = empty()
    override val signal = method<R, E> {
        try {
            callback(it) {
                output(it)
            }
        } catch (e:Throwable) {
            report()
            error(e)
        }
    }
}

class MapFutureOperator<in R, E>(method:(R)->Future<E>):Operator<R, E>(){
    override val error = empty<Throwable>()
    override val report = empty()
    override val signal = method<R, E> {
        try {
            output(method(it).get())
        } catch (e:Throwable) {
            report()
            error(e)
        }
    }
}

class MapOperator<in R, E>(transform: (R) -> E) : Operator<R, E>() {

    override val error = empty<Throwable>()
    override val report = empty()
    override val signal = method<R, E> {
            try {
                output(transform(it))
            } catch (e : Throwable) {
                report()
                error(e)
            }
        }
}

class ForEachOperator<T>(block:(T)->Unit):EasyOperator<T>() {
    override val error = empty<Throwable>()
    override val report = empty()
    override val signal = method<T> {
        try {
            block(it)
            report()
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

class TakeOperator<T>(private val number:Int) : EasyOperator<T>() {

    var count = AtomicInteger(0)
    var report_count = AtomicInteger(0)
    //var finished = true

    override val signal = method<T> {

        val c = count.incrementAndGet()
        when {
            c < number -> output(it)
            c == number -> {
                output(it)
                cancel()
            }
            else -> report()
        }
    }

    override val finish = empty()

    override val cancel = empty()

    override val report = method {
        output()
        if (report_count.incrementAndGet()>=number) {
            finish.output()
        }
    }

}

class ScheduleOperator<T>(val scheduler : Executor) : EasyOperator<T>() {

    override val signal = method<T> {
        scheduler.execute { output(it) }
    }

    override val error = method<Throwable> {
        scheduler.execute {output(it)}
    }

    override val finish = method {
        scheduler.execute { output() }
    }
}

class LogOperator<T>(log:(T)->String) : EasyOperator<T>() {
    override val signal = method<T> {
        println(log(it))
        output(it)
    }
}

class PackOperator<T>(private val n:Int):EasyOperator<T>(){

    val queue : LinkedTransferQueue<T> = LinkedTransferQueue()

    var finished = false
    var count = 0

    override val report = method {
        synchronized(this) {
            count--
            println("report:$count")
            doo()
        }
    }

    override val signal = method<T> {
        queue.add(it)
        report.output()
        doo()
    }

    override val finish = method {
        finished = true
        doo()
    }

    override val cancel = method {
        signal.output = {}
    }

    @Synchronized fun doo() {

        if (count<=0) {
            if (queue.size>=n) {
                count = n
                for (i in 0 until n) {
                    signal.output(queue.take())
                }

            } else if (finished) {
                if (queue.isNotEmpty()) {
                    count = queue.size
                    for (i in 0 until queue.size) {
                        signal.output(queue.take())
                    }
                } else {
                    finish.output()
                }
            }
        }
    }
}

/*class SourceToStream<T>(val source: Source<T>) : BaseStream<T>(), Receiver<T> {

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

    override fun signal(v: T) {
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
}*/