package io.rxk

//interface Receiver<in T> {
//    fun next(v:T)
//    fun error(e:Throwable)
//    fun finish()
//}
//
//class Finish<T>(val block: () -> Unit) : Receiver<T> {
//    override fun next(v: T) {}
//    override fun error(e: Throwable) {}
//    override fun finish() {
//        block()
//    }
//}