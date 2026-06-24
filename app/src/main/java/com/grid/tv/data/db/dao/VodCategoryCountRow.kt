package com.grid.tv.data.db.dao

data class VodCategoryCountRow(
    val playlistId: Long,
    val categoryId: String,
    val streamCount: Int
)
