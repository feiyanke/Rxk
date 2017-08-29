package io.rxk

import java.util.concurrent.LinkedTransferQueue

interface IOperation<E1, E2> : IContextBuilder<E1, E2>
interface IEasyOperation<T> : IOperation<T, T>

class AsStream<S> : IEasyOperation<S> {

    private object finished
    private val queue : LinkedTransferQueue<Any> = LinkedTransferQueue()
    private val next = method<S, S> { queue.add(it) }
    private val error = errorMethod { queue.add(it) }
    private val finish = finishMethod { queue.add(finished) }
    private val reset = resetLambda { queue.clear() }
    private val request = requestLambda {
        for (i in 0 until it) {
            val a = queue.take()
            if (a is Throwable) {
                error(a)
            } else if (a == finished) {
                finish()
            } else {
                next(a as S)
            }
        }
        it
    }

    override fun makeContext(ctx: IContext<*, S>): IContext<*, S> {
        return ctx.make(next, error, finish, request, reset)
    }
}