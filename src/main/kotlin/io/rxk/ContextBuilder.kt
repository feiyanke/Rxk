package io.rxk

interface IContextBuilder<E1, E2> {
    fun makeContext(ctx:IContext<*, E1> = EmptyContext()) : IContext<*, E2>
}

class ContextBuilder {
}