package io.rxk.chain

abstract class Method<in T, R> : (T)->Unit {

    fun out(o:(R)->Unit) : Method<T, R> {
        output = o
        return this
    }
    open var output : (R)->Unit = {}
    fun <E> chain(method: Method<R, E>) : Method<T, E> {
        return Chain(this, method)
    }
}

class MethodBase<in T, R>(private val input: Method<T, R>.(T)->Unit) : Method<T, R>() {
    override fun invoke(p1: T) = input(p1)
}

class Chain<in T, E, R>(a: Method<T, E>, b: Method<E, R>) : Method<T, R>() {

    val start : Method<T, *> = if (a is Chain<T, *, *>) a.start else a
    val end : Method<*, R> = b
    init {
        a.output = b
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

class Filter<T>(private val predicate: (T) -> Boolean): Method<T,T>() {
    override fun invoke(p1: T) {
        if (predicate(p1)) output(p1)
    }
}