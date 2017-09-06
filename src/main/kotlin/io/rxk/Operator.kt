package io.rxk

import java.util.concurrent.Executor

abstract class Operator<in T, R> {
    abstract val next : IMethod<T, R>
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IUnitMethod? = null

    open val start : IUnitMethod? = null
    open val cancel : IUnitMethod? = null
    open val report : IUnitMethod? = null
}

abstract class EasyOperator<R> {

    open val next: IEasyMethod<R>? = null
    open val error : IEasyMethod<Throwable>? = null
    open val finish : IUnitMethod? = null

    open val start : IUnitMethod? = null
    open val cancel : IUnitMethod? = null
    open val report : IUnitMethod? = null
}

class FilterOperator<T>(predicate: (T) -> Boolean) : EasyOperator<T>() {
    override val error = empty<Throwable>()
    override val report = empty()
    override val next = object : EasyMethod<T>() {
        override fun invoke(p1: T) {
            try {
                if (predicate(p1)) output(p1)
                else report()
            } catch (e : Throwable) {
                error(e)
            }
        }
    }
}

class MapCallbackOperator<in R, E>(callback:(R, (E)->Unit)->Unit):Operator<R, E>(){
    override val error = empty<Throwable>()
    override val next = method<R, E> {
        try {
            callback(it) {
                output(it)
            }
        } catch (e:Throwable) {
            error(e)
        }
    }
}



class MapOperator<in R, E>(transform: (R) -> E) : Operator<R, E>() {

    override val error = empty<Throwable>()

    override val next = method<R, E> {
            try {
                output(transform(it))
            } catch (e : Throwable) {
                error(e)
            }
        }
}

class ForEachOperator<T>(block:(T)->Unit):EasyOperator<T>() {
    override val error = empty<Throwable>()
    override val report = empty()
    override val next = method<T> {
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

class TakeOperator<T>(val number:Int) : EasyOperator<T>() {

    var count = 0
    var finished = false;
    override val finish = method {}
    override val cancel = empty()
    override val report = empty()
    override val next = method<T> {
        if (finished) {
            report()
        } else {
            if (count < number) {
                count++
                output(it)
            } else {
                finished = true
                cancel()
                finish.output()
            }
        }
    }

    override val error = method<Throwable> {
        if (finished) {
            report()
        } else {
            if (count < number) {
                count++
                output(it)
            } else {
                finished = true
                cancel()
                finish.output()
            }
        }

    }
}

class ScheduleOperator<T>(val scheduler : Executor) : EasyOperator<T>() {

    override val next = method<T> {
        scheduler.execute { synchronized(this@ScheduleOperator) {output(it)} }
    }

    override val error = method<Throwable> {
        scheduler.execute { synchronized(this@ScheduleOperator) {output(it)} }
    }

    override val finish = method {
        scheduler.execute { synchronized(this@ScheduleOperator) {output()} }
    }
}

class LogOperator<T>(log:(T)->String) : EasyOperator<T>() {
    override val next = method<T> {
        println(log(it))
        output(it)
    }
}

class PackOperator<T>(n:Int):EasyOperator<T>(){
    
}