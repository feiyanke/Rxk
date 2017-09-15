package io.rxk

interface ISink<R> {
    var output : (R)->Unit
}

interface IMethod<in T, R> : ISink<R>, (T)->Unit

interface IEasyMethod<T> : IMethod<T, T>

interface IUnitMethod : IEasyMethod<Unit>, ()->Unit {
    override fun invoke() = invoke(Unit)
    fun output() = output(Unit)
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

abstract class UnitMethod : EasyMethod<Unit>(), IUnitMethod

open class EmptyMethod<T> : EasyMethod<T>() {
    override fun invoke(v: T) = output(v)
}

open class EmptyUnitMethod : UnitMethod() {
    override fun invoke(v: Unit) = output(v)
}

open class Chain<in T, E, R>(a: IMethod<T, E>, b: IMethod<E, R>) : IChain<T, R> {
    override val start : IMethod<T, *> = (a as? IChain<T, E>)?.start ?: a
    override val end : IMethod<*, R> = (b as? IChain<E, R>)?.end ?: b
    init { a.output = (b as? IChain<E, R>)?.start ?: b }
}

class EasyChain<T>(a: IEasyMethod<T>, b: IEasyMethod<T>) : Chain<T, T, T>(a,b), IEasyChain<T>
class UnitChain(a: IUnitMethod, b: IUnitMethod) : Chain<Unit, Unit, Unit>(a,b), IUnitMethod

fun empty() = EmptyUnitMethod()
fun <T> empty() = EmptyMethod<T>()

inline fun <T, R> method(crossinline block:IMethod<T, R>.(T)->Unit) : IMethod<T, R> {
    return object : Method<T, R>() {
        override fun invoke(v: T) {
            block(v)
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

inline fun method(crossinline block: IUnitMethod.() -> Unit) : IUnitMethod {
    return object : UnitMethod() {
        override fun invoke(p1: Unit) {
            block()
        }

    }
}


fun <T, R> IMethod<T, R>.out(o:(R)->Unit):IMethod<T, R> = apply { output = o }
fun <T, E, R> IMethod<T, R>.chain(method: IMethod<R, E>) : IMethod<T, E> = Chain(this, method)
fun <T> IEasyMethod<T>.chain(method: IEasyMethod<T>) : IEasyMethod<T> = EasyChain(this, method)
fun IUnitMethod.chain(method: IUnitMethod) : IUnitMethod = UnitChain(this, method)
