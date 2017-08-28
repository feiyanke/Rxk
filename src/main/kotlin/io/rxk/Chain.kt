package io.rxk.chain

abstract class Method<in T, R> : (T)->Unit {

    fun out(o:(R)->Unit) : Method<T, R> {
        output = o
        return this
    }
    open var output : (R)->Unit = {}

    open fun <E> chain(method: Method<R, E>) : Method<T, E> {
        return  Chain(this, method)
    }

}

abstract class EasyMethod<T> : Method<T, T>()

class EmptyMethod<T> : EasyMethod<T>() {
    override fun invoke(p1: T) = output(p1)
}

class EasyChain<T>(a: Method<T, T>, b: Method<T, T>) : Chain<T, T, T>(a, b)

class MethodBase<in T, R>(private val input: Method<T, R>.(T)->Unit) : Method<T, R>() {
    override fun invoke(p1: T) = input(p1)
}

open class Chain<T, E, R>(a: Method<T, E>, b: Method<E, R>) : Method<T, R>() {
    /*val start : Method<T, *> = if (a is Chain<T, *, *>) a.start else a
    val end : Method<*, R> = b*/
    val start : Method<T, *>
    val end : Method<*, R>

    init {
        start = (a as? Chain<T, *, E>)?.start ?: a
        a.output = (b as? Chain<E, *, R>)?.start ?: b
        end = (b as? Chain<E, *, R>)?.end ?: b
    }

    override var output: (R) -> Unit
        get() = end.output
        set(value) {end.output = value}

    override fun invoke(p1: T) = start(p1)
}

class CallbackMap<in T, R>(private val transform: (T, (R)->Unit) -> Unit): Method<T, R>() {
    override fun invoke(p1: T) {
        transform(p1) {
            output(it)
        }
    }
}

class Map<in T, R>(private val transform:(T)->R): Method<T, R>() {
    override fun invoke(p1: T) = output(transform(p1))
}

class Filter<T>(private val predicate: (T) -> Boolean): EasyMethod<T>() {
    override fun invoke(p1: T) {
        if (predicate(p1)) output(p1)
    }
}