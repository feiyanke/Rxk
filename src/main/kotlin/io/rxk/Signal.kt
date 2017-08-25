package io.rxk

interface Signal<T> {
    var receiver : Receiver<T>?
}