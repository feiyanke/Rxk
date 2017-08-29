package io.rxk

open class Signal<S> : EasyMethod<S>() {
    override fun invoke(s: S) = output(s)
    open fun next(v:S) = output(v)
    open val error : IEasyMethod<Throwable> = EmptyMethod()
    open val finish : IUnitMethod = EmptyUnitMethod()
    open val request : IEasyMethod<Int> = EmptyMethod()
    open val reset : IUnitMethod = EmptyUnitMethod()
    fun makeContext(): Context<S, S> = Context(this, error, finish, request, reset)
}




