package io.rxk

import java.util.concurrent.Callable
import java.util.concurrent.Future

fun <T> Iterable<T>.asStream():Context<T,T> = Context.from(this)
fun <T> Array<T>.asStream():Context<T, T> = Context.from(this)
fun Runnable.asStream():Context<Unit, Unit> = Context.from(this)
fun <T> Callable<T>.asStream():Context<T, T> = Context.from(this)
fun <T> Future<T>.asStream():Context<T, T> = Context.from(this)