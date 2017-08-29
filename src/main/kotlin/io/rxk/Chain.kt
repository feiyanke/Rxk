package io.rxk

interface IMethod<in T, R> : (T)->Unit, ()->Unit {
    override fun invoke() {}
    var output : (R)->Unit
}

inline fun <T, R> method(crossinline block:IMethod<T, R>.(T)->Unit) : IMethod<T, R> {
    return object : Method<T, R>() {
        override fun invoke(v: T) {
            block(v)
        }
    }
}

inline fun <T, R> lambda(crossinline block:(T)->R) : IMethod<T, R> {
    return object : Method<T, R>() {
        override fun invoke(v: T) {
            output(block(v))
        }
    }
}

inline fun <T> method(crossinline block:IEasyMethod<T>.(T)->Unit) : IEasyMethod<T> {
    return object : EasyMethod<T>() {
        override fun invoke(v: T) {
            block(v)
        }
    }
}

inline fun <T> lambda(crossinline block:(T)->T) : IEasyMethod<T> {
    return object : EasyMethod<T>() {
        override fun invoke(v: T) {
            output(block(v))
        }
    }
}

fun <T, R> IMethod<T, R>.out(o:(R)->Unit):IMethod<T, R> = apply { output = o }
fun <T, E, R> IMethod<T, R>.chain(method: IMethod<R, E>) : IMethod<T, E> = Chain(this, method)

interface IEasyMethod<T> : IMethod<T, T>

fun <T> IEasyMethod<T>.chain(method: IEasyMethod<T>) : IEasyMethod<T> {
    return EasyChain(this, method)
}

interface IChain<in T, R> : IMethod<T, R> {
    val start : IMethod<T, *>
    val end : IMethod<*, R>

    override var output: (R) -> Unit
        get() = end.output
        set(value) {end.output = value}

    override fun invoke(p1: T) = start(p1)
}

interface IEasyChain<T> : IChain<T, T>, IEasyMethod<T>

abstract class Method<in T, R> : IMethod<T, R> {
    override var output: (R) -> Unit = {}
}

abstract class EasyMethod<T> : Method<T, T>(), IEasyMethod<T>

open class EmptyMethod<T> : EasyMethod<T>() {
    override fun invoke(p1: T) = output(p1)
}

open class UnitMethod : EmptyMethod<Unit>() {
    override fun invoke() = invoke(Unit)
}

open class Chain<in T, E, R>(a: IMethod<T, E>, b: IMethod<E, R>) : IChain<T, R> {
    override val start : IMethod<T, *> = (a as? IChain<T, E>)?.start ?: a
    override val end : IMethod<*, R> = (b as? IChain<E, R>)?.end ?: b
    init { a.output = (b as? IChain<E, R>)?.start ?: b }
}

class EasyChain<T>(a: IEasyMethod<T>, b: IEasyMethod<T>) : Chain<T, T, T>(a,b), IEasyChain<T>