package io.rxk

interface ISignal<T> : IMethod<T, T> {
    val context : Context<T, T> get() = Context(this)
}

//abstract class Signal<T> : EasyMethod<T>(), ISignal<T> {
//    override val context: Context<T, T> get() = Context(this)
//}

fun <T> ISignal<T>.next(v:T) = context.next(v)
fun ISignal<*>.error(e:Throwable) = context.error(e)
fun ISignal<*>.finish() = context.finish(Unit)
fun ISignal<*>.requst(n:Int = 1) = context.request(n)
fun ISignal<*>.reset() = context.reset()