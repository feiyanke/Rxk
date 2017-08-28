package io.rxk

class EmptyContext<T> : Context<T, T>(EmptyMethod())

open class Context<T, R> (
    var next : IMethod<T, R>,
    var error : IEasyMethod<Throwable> = EmptyMethod(),
    var finish : IEasyMethod<Unit> = EmptyMethod(),
    var request : IEasyMethod<Int> = EmptyMethod(),
    var reset : IEasyMethod<Unit> = EmptyMethod()
) {
    companion object {
        fun <T> from(iterable: Iterable<T>) : Context<T, T> {
            return IterableSteam(iterable).context
        }
    }

    fun <E> chainNext(m:IMethod<R, E>) : Context<T, E> {
        return Context(next.chain(m), error, finish, request, reset)
    }

    fun chainNext(m:IEasyMethod<R>) : Context<T, R> = apply{
        next = next.chain(m)
    }

    fun chainError(m:IEasyMethod<Throwable>) : Context<T, R> = apply{
        error = error.chain(m)
    }

    fun chainFinish(m:IEasyMethod<Unit>) : Context<T, R> = apply{
        finish = finish.chain(m)
    }

    fun chainRequest(m:IEasyMethod<Int>) : Context<T, R> = apply{
        request = m.chain(request)
    }

    fun chainReset(m:IEasyMethod<Unit>) : Context<T, R> = apply{
        reset = m.chain(reset)
    }
}

abstract class TestStart<T> {
    abstract fun reset()
    abstract fun request(n: Int = 1)
    val context : Context<T, T> = EmptyContext<T>().apply {
        request.out{this@TestStart.request(it)}
        reset.out { this@TestStart.reset() }
    }
}

class IterableSteam<T>(val iterable: Iterable<T>) : TestStart<T>() {

    var iter : Iterator<T> = iterable.iterator()

    override fun request(n: Int) {
        for (i in 0..(n-1)) {
            try {
                if (iter.hasNext()) {
                    context.next(iter.next())
                } else {
                    context.finish(Unit)
                }
            } catch (e:Throwable) {
                context.error(e)
            }
        }
    }

    override fun reset() {
        iter = iterable.iterator()
    }
}

/*fun <T> from(iterable: Iterable<T>) : Context<T, T> {
    return Context<T, T>(EmptyMethod()).apply {
        var iter : Iterator<T> = iterable.iterator()
        request.out {
            for (i in 0 until it) {
                try {
                    if (iter.hasNext()) {
                        next(iter.next())
                    } else {
                        finish(Unit)
                    }
                } catch (e:Throwable) {
                    error(e)
                }
            }
        }
        reset.out {
            iter = iterable.iterator()
        }
    }
}*/

/*abstract class Operation<R, E>(c: Context<*, R>) {
    abstract val next : Method<R, E>
    open val error : EmptyMethod<Throwable>? = null
    open val finish : EmptyMethod<Unit>? = null
    open val request : EmptyMethod<Int>? = null
    open val reset : EmptyMethod<Unit>? = null
    val context : Context<*, E>
    init {
        context = c.chainNext(next).chainError(error).chainFinish(finish).chainRequest(request).chainReset(reset)
    }
}

abstract open class EasyOperation<T>(c: Context<*, T>) : Operation<T, T>(c)

class FilterOperation<T>(c:Context<*, T>, predicate: (T) -> Boolean) : EasyOperation<T>(c) {
    override val error = EmptyMethod<Throwable>()
    override val request = EmptyMethod<Int>()
    override val next = object : EasyMethod<T>() {
        override fun invoke(p1: T) {
            try {
                if (predicate(p1)) output(p1)
                else request(1)
            } catch (e : Throwable) {
                error(e)
            }
        }
    }
}


class MapOperation<R, E>(c: Context<*, R>, transform: (R) -> E) {

    private val error = EmptyMethod<Throwable>()

    private val next = object : Method<R, E>() {
        override fun invoke(p1: R) {
            try {
                output(transform(p1))
            } catch (e : Throwable) {
                error(e)
            }
        }
    }
}*/

fun <R, E> Context<*, R>.map(transform: (R) -> E) : Context<*, E> {
    val error = EmptyMethod<Throwable>()
    val next = object : Method<R, E>() {
        override fun invoke(p1: R) {
            try {
                output(transform(p1))
            } catch (e : Throwable) {
                error(e)
            }
        }
    }
    return chainNext(next).chainError(error)
}

fun <R> Context<*, R>.filter(predicate: (R) -> Boolean) : Context<*, R> {
    val error = EmptyMethod<Throwable>()
    val request = EmptyMethod<Int>()
    val next = object : EasyMethod<R>() {
        override fun invoke(p1: R) {
            try {
                if (predicate(p1)) output(p1)
                else request(1)
            } catch (e : Throwable) {
                error(e)
            }
        }
    }
    return chainNext(next).chainError(error).chainRequest(request)
}

fun <R> Context<*, R>.forEach(n:Int = 1, block:(R)->Unit):Context<*, R> {
    val error = EmptyMethod<Throwable>()
    next.out {
        try {
            block(it)
            request(1)
        } catch (e:Throwable) {
            error(e)
        }
    }
    return chainError(error)
}

fun Context<*, *>.finish(block: () -> Unit) : Context<*, *> {
    finish.out {
        block()
    }
    return this
}

fun Context<*, *>.error(block: (e:Throwable) -> Unit) : Context<*, *> {
    error.out {
        block(it)
    }
    return this
}

fun Context<*, *>.start() {
    reset(Unit)
    request(1)
}

fun main(args: Array<String>) {
    Context.from(0..100)
            .map { it*it }
            .filter { it % 2 == 0 }
            .map { it.toString() }
            //.filter { it.length < 3 }
            .forEach { println(it) }
            .finish {
                println("finished")
            }
            .error {println("error : " + it.printStackTrace())}
            .start()
}