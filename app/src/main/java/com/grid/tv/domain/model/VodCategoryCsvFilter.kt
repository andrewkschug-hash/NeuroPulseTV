package com.grid.tv.domain.model

/** Pads category filter ids for Room CSV membership binds (unused slots never match). */
fun categoryCsvFilterSlots(categoryIds: Collection<String>, size: Int = 8): List<String> {
    val none = "\u0001" // never a real Xtream category id
    val normalized = categoryIds.mapNotNull { VodCategoryId.canonicalize(it) }.distinct()
    return (normalized + List(size) { none }).take(size)
}
