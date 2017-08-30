package io.rxk

open class SignalContext<T, R> (
        var next : IMethod<T, R>,
        var error : IEasyMethod<Throwable>,
        var finish : IEasyMethod<Unit>
) {
    fun asStream() = makeStream(SignalToStream())
    fun filter(predicate:(R)->Boolean) = make(FilterOperator(predicate))
    fun <E> map(tranform:(R)->E) = make(MapOperator(tranform))
    fun forEash(count:Int = 0, block:(R)->Unit) = make(ForEachOperator(count, block))

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
}




