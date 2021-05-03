package de.chrgroth.james

// TODO maybe move to persistence adapter side??
interface CrudRepository<Type, Id> {
    fun get(id: Id): Maybe<Type?>

    // TODO what about paging?!?
    // TODO have no clue how to design filter parameters right now, cause this is DB specific
    fun find(): Maybe<Set<Type>>

    // TODO upsert instead of create and update??
    fun create(item: Type): Maybe<Type>
    fun update(item: Type): Maybe<Type>

    fun delete(id: Id): Maybe<Unit>
}
