package io.rxk

import java.util.concurrent.Callable
import java.util.concurrent.Future

fun <T:Any> Iterable<T>.asStream():Context<T,T> = Context.from(this)
fun <T:Any> Array<T>.asStream():Context<T, T> = Context.from(this)
fun Runnable.asStream():Context<Unit, Unit> = Context.from(this)
fun <T:Any> Callable<T>.asStream():Context<T, T> = Context.from(this)
fun <T:Any> Future<T>.asStream():Context<T, T> = Context.from(this)