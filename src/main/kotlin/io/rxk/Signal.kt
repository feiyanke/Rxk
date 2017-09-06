package io.rxk

import java.util.concurrent.*

class Context<T, R> (
    var next: IMethod<T, R>,
    var error: IEasyMethod<Throwable>,
    var finish: IUnitMethod,

    var start: IUnitMethod,
    var cancel: IUnitMethod,
    var report: IUnitMethod
) {

    companion object {
        private fun <T> make(o: Stream<T>) = Context(o.next, o.error, o.finish, o.start, o.cancel, o.report)

        fun <S> create(block:Stream<S>.()->Unit) : Context<S, S> {
            return make(object : Stream<S>() {
                override val start = method {
                    try {
                        block()
                    } catch (e:Throwable) {
                        error(e)
                        finish()
                    }
                }
            })
        }

        fun fromRunable(block:()->Unit):Context<Unit, Unit> {
            return make( object : Stream<Unit>() {
                override val start = method {
                    try {
                        block()
                    } catch (e:Throwable) {
                        error(e)
                    } finally {
                        finish()
                    }
                }
            })
        }

        fun from(runnable: Runnable):Context<Unit, Unit> {
            return fromRunable(runnable::run)
        }

        fun <T> fromCallable(callable:()->T) : Context<T, T> {
            return make( object : Stream<T>() {
                override val start = method {
                    try {
                        next(callable())
                    } catch (e:Throwable) {
                        error(e)
                    } finally {
                        finish()
                    }
                }
            })
        }

        fun <T> from(callable: Callable<T>):Context<T, T> {
            return fromCallable(callable::call)
        }

        fun <T> from(future: Future<T>):Context<T, T> {
            return fromCallable(future::get)
        }

        fun interval(ms: Long):Context<Int, Int> {
            return make(object : Stream<Int>() {
                var count = 0
                override val start = method {
                    count = 0
                    Executors.newSingleThreadScheduledExecutor().scheduleAtFixedRate({
                        next(count)
                        count++
                    }, 0, ms, TimeUnit.MILLISECONDS)
                }
            })
        }
    }

    fun <E> make(m: Operator<R, E>) = make(m.next, m.error, m.finish, m.start, m.cancel, m.report)
    fun make(m: EasyOperator<R>) = make(m.next, m.error, m.finish, m.start, m.cancel, m.report)

    fun <E> make(next : IMethod<R, E>,
                                error : IEasyMethod<Throwable>? = null,
                                finish : IUnitMethod? = null,
                                start : IUnitMethod? = null,
                                cancel : IUnitMethod? = null,
                                report : IUnitMethod? = null
    ) : Context<T, E> = chainNext(next).apply {
        chainError(error)
        chainFinish(finish)
        chainStart(start)
        chainCancel(cancel)
        chainReport(report)
    }

    fun make(next : IEasyMethod<R>? = null,
                            error : IEasyMethod<Throwable>? = null,
                            finish : IUnitMethod? = null,
                            start : IUnitMethod? = null,
                            cancel : IUnitMethod? = null,
                            report : IUnitMethod? = null
    ) : Context<T, R> = apply {
        chainNext(next)
        chainError(error)
        chainFinish(finish)
        chainStart(start)
        chainCancel(cancel)
        chainReport(report)
    }

    private fun <E> chainNext(m:IMethod<R, E>) : Context<T, E> = Context(next.chain(m), error, finish, start, cancel, report)
    private fun chainNext(m:IEasyMethod<R>?) = m?.let { next = next.chain(m) }
    private fun chainError(m:IEasyMethod<Throwable>?) = m?.let { error = error.chain(m) }
    private fun chainFinish(m:IUnitMethod?) = m?.let { finish = finish.chain(m) }
    private fun chainStart(m:IUnitMethod?) = m?.let { start = start.chain(m) }
    private fun chainCancel(m:IUnitMethod?) = m?.let { cancel = cancel.chain(m) }
    private fun chainReport(m:IUnitMethod?) = m?.let { report = report.chain(m) }


    fun filter(predicate:(R)->Boolean) = make(FilterOperator(predicate))
    fun <E> map(tranform:(R)->E) = make(MapOperator(tranform))
    fun forEash(count:Int = 0, block:(R)->Unit) = make(ForEachOperator(count, block))
    fun finish(block: () -> Unit) = make(FinishOperator(block))
    fun error(block: (e:Throwable) -> Unit) = make(ErrorOperator(block))
    fun take(n:Int) = make(TakeOperator(n))
    fun on(executor: Executor) = make(ScheduleOperator(executor))
}

/*open class SignalContext<T, R> (
        var next : IMethod<T, R>,
        var error : IEasyMethod<Throwable>,
        var finish : IEasyMethod<Unit>
) {
    fun asStream() = makeStream(SignalToStream())
    fun filter(predicate:(R)->Boolean) = make(FilterOperator(predicate))
    fun <E> map(tranform:(R)->E) = make(MapOperator(tranform))
    fun forEash(count:Int = 0, block:(R)->Unit) = make(ForEachOperator(count, block))
    fun finish(block: () -> Unit) = make(FinishOperator(block))
    fun error(block: (e:Throwable) -> Unit) = make(ErrorOperator(block))
    fun take(n:Int) = make(TakeOperator(n))
    fun on(executor: Executor) = make(ScheduleOperator(executor))

    private fun makeStream(m:SignalToStreamOperator<R>) : StreamContext<T, R> {
        return StreamContext<T, R>(next, error, finish, m.reset, m.request).apply {
            chainNext(m.next)
            chainError(m.error)
            chainFinish(m.finish)
        }
    }

    protected fun <E> make(m: Operator<R, E>) = make(m.next, m.error, m.finish, m.request, m.reset)
    protected fun make(m: EasyOperator<R>) = make(m.next, m.error, m.finish, m.request, m.reset)

    open protected fun <E> make(next : IMethod<R, E>,
                         error : IEasyMethod<Throwable>? = null,
                         finish : IEasyMethod<Unit>? = null,
                         request : IEasyMethod<Int>? = null,
                         reset : IEasyMethod<Unit>? = null
    ) : SignalContext<T, E> = chainNext(next).apply {
        chainError(error)
        chainFinish(finish)
    }

    open protected fun make(next : IEasyMethod<R>? = null,
                     error : IEasyMethod<Throwable>? = null,
                     finish : IEasyMethod<Unit>? = null,
                     request : IEasyMethod<Int>? = null,
                     reset : IEasyMethod<Unit>? = null
    ) : SignalContext<T, R> = apply {
        chainNext(next)
        chainError(error)
        chainFinish(finish)
    }

    private fun <E> chainNext(m:IMethod<R, E>) : SignalContext<T, E>
            = SignalContext(next.chain(m), error, finish)
    protected fun chainNext(m:IEasyMethod<R>?) {
        if (m!=null) next = next.chain(m)
    }

    protected fun chainError(m:IEasyMethod<Throwable>?) {
        if (m!=null) error = error.chain(m)
    }

    protected fun chainFinish(m:IEasyMethod<Unit>?) {
        if (m!=null)finish = finish.chain(m)
    }
}

open class Signal<S> : EasyMethod<S>() {
    override fun invoke(s: S) = output(s)
    open fun next(v:S) = output(v)
    open val error : IEasyMethod<Throwable> = EmptyMethod()
    open val finish : IUnitMethod = EmptyUnitMethod()
    open fun makeContext(): SignalContext<S, S> = SignalContext(this, error, finish)
}*/




