package io.rxk

import java.util.*
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

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

class ForEachOperator<T>(report:Boolean, block:(T)->Unit):EasyOperator<T>() {
    override val error = empty<Throwable>()
    override val report = empty()
    override val signal = method<T> {
        try {
            block(it)
            if (report) report()
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

    var queue : MutableList<T> = mutableListOf()
    val pack : MutableList<List<T>> = mutableListOf()

    var report_count = 0

    override val report = method {
        synchronized(this) {
            report_count--
            doo()
        }

    }

    override val signal = method<T> {
        synchronized(this) {
            queue.add(it)
            if (queue.size==n) {
                pack.add(queue)
                queue = LinkedList()
            }
            doo()
        }
        report.output()
    }

    override val finish = method {
        synchronized(this) {
            pack.add(queue)
            doo()
        }
    }

    override val cancel = method {
        signal.output = {}
    }

    private fun doo() {
        if (report_count==0&&pack.isNotEmpty()) {
            val values = pack[0]
            pack.removeAt(0)
            when {
                values.isEmpty() -> finish.output()
                values.size<n -> report_count = values.size-1
                else -> report_count = n
            }
            values.forEach {
                signal.output(it)
            }
        } else if (report_count == -1) {
            finish.output()
        }
    }
}

class ScanOperator<T>(private var value:T, method:(T, T)->T):EasyOperator<T>(){
    override val signal = method<T> {
        synchronized(this) {
            value = method(value, it)
        }
        output(value)
    }
}

class MultiScanOperator<T>(vararg values:T, method: (List<T>, T) -> T):EasyOperator<T>(){
    private val list = values.toMutableList()
    override val signal = method<T> {
        synchronized(this) {
            list.add(method(list, it))
            list.removeAt(0)
        }
        output(list.last())
    }
}

class DistinctOperator<T> : EasyOperator<T>(){
    private val set = mutableSetOf<T>()
    override val signal = method<T> {
        val r = synchronized(set) {
            if (set.contains(it)) {
                report()
                false
            } else {
                set.add(it)
                true
            }
        }
        if (r) output(it)
    }
    override val report = empty()
}

class BufferOperator<T>(count:Int) : Operator<T, List<T>>() {
    private var list = mutableListOf<T>()
    override val signal = method<T, List<T>> {
        var out :List<T>? = null
        synchronized(list) {
            list.add(it)
            if (list.size == count) {
                out = list
                list = mutableListOf()
            } else {
                report()
            }
        }
        out?.let { output(it) }
    }
    override val report = empty()
}

class FlatMapOperator<in T, R>(transform:(T)->Context<*, R>):Operator<T, R>() {
    private var count = AtomicInteger(0)
    override val signal = method<T, R> {
        transform(it).forEach {
            count.incrementAndGet()
            output(it)
        }.error {
            error(it)
        }.finish {
            report.output()
        }.start()
    }
    override val error = empty<Throwable>()
    override val report = method {
        if (count.decrementAndGet()==-1) {
            finish.output()
        }
    }
    override val finish = method {
        if (count.decrementAndGet()==-1) {
            output()
        }
    }
}

class ElementAtOperator<T>(index:Int) : EasyOperator<T>() {
    private var count = 0
    override val signal = method<T> {
        synchronized(this) {
            if (count == index) {
                output(it)
                cancel()
            } else {
                count++
                report.output()
            }
        }
    }
    override val cancel = empty()
    override val report = method {
        finish.output()
    }
    override val finish = method {  }
}

class LastOperator<T> : EasyOperator<T>() {
    private val last : AtomicReference<T> = AtomicReference()
    override val signal:IEasyMethod<T> = method<T> {
        last.set(it)
        report.output()
    }
    override val report = method {
        finish.output()
    }
    override val finish = method {
        signal.output(last.get())
    }
}

