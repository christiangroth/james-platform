package de.foo.bar

import org.litote.kmongo.Id

data class Foo(
    val _id: Id<Foo>?,
	val id: Long,
	val description: String?
)

data class Bar(
    val _id: Id<Bar>?,
	val id: Long,
	val version: Long
)

data class Hidden(
    val _id: Id<Hidden>?,
	val name: String
)
