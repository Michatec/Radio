package com.michatec.radio.extensions


/* Creates a "real" copy of an ArrayList<Long> - useful for preventing concurrent modification issues */
fun ArrayList<Long>.copy(): ArrayList<Long> {
    val copy: ArrayList<Long> = ArrayList()
    this.forEach { copy.add(it) }
    return copy
}
