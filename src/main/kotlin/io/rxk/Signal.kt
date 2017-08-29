package io.rxk

interface ISignal<S> : IEasyMethod<S>, IContextBuilder<S, S> {
    fun next(v:S) = output(v)
    override fun invoke(s: S) = output(s)
    override fun makeContext(ctx: IContext<*, S>): IContext<*, S> = Context(this)
}




