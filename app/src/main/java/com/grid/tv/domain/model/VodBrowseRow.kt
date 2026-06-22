package com.grid.tv.domain.model

data class VodBrowseRow(
    val id: String,
    val title: String,
    val movies: List<VodItem> = emptyList(),
    val series: List<SeriesShow> = emptyList()
) {
    val isEmpty: Boolean get() = movies.isEmpty() && series.isEmpty()
}

fun buildMovieBrowseRows(
    movies: List<VodItem>,
    categories: List<VodCategory>,
    maxRows: Int = 16,
    itemsPerRow: Int = 20
): List<VodBrowseRow> {
    if (movies.isEmpty()) return emptyList()
    val rows = mutableListOf<VodBrowseRow>()
    val byCategory = movies.groupBy { it.categoryId.orEmpty() }
    val categoryNameById = categories.associate { it.id to it.name }

    movies.sortedByDescending { it.addedEpochSec ?: 0L }
        .take(itemsPerRow)
        .takeIf { it.isNotEmpty() }
        ?.let { rows += VodBrowseRow("recent", "Recently Added", movies = it) }

    movies.filter { parseVodRating(it) > 0.0 }
        .sortedByDescending { parseVodRating(it) }
        .take(itemsPerRow)
        .takeIf { it.isNotEmpty() }
        ?.let { rows += VodBrowseRow("top_imdb", "Top IMDB", movies = it) }

    movies.filter { titleLooks4K(it.title) }
        .take(itemsPerRow)
        .takeIf { it.isNotEmpty() }
        ?.let { rows += VodBrowseRow("4k", "4K Movies", movies = it) }

    categories.distinctBy { it.id }
        .sortedBy { it.name.lowercase() }
        .forEach { category ->
            val items = byCategory[category.id].orEmpty().take(itemsPerRow)
            if (items.isNotEmpty()) {
                rows += VodBrowseRow("cat_${category.id}", category.name, movies = items)
            }
        }

    byCategory.entries
        .filter { (id, _) -> id.isNotBlank() && id !in categoryNameById }
        .sortedBy { (id, _) -> id.lowercase() }
        .forEach { (id, items) ->
            val label = categoryNameById[id] ?: id
            rows += VodBrowseRow("cat_$id", label, movies = items.take(itemsPerRow))
        }

    return rows.filter { !it.isEmpty }.distinctBy { it.id }.take(maxRows)
}

fun buildSeriesBrowseRows(
    shows: List<SeriesShow>,
    categories: List<String>,
    maxRows: Int = 16,
    itemsPerRow: Int = 20
): List<VodBrowseRow> {
    if (shows.isEmpty()) return emptyList()
    val rows = mutableListOf<VodBrowseRow>()
    val byCategory = shows.groupBy { it.categoryId.orEmpty() }

    shows.sortedBy { it.name.lowercase() }
        .take(itemsPerRow)
        .takeIf { it.isNotEmpty() }
        ?.let { rows += VodBrowseRow("all", "All Series", series = it) }

    shows.filter { titleLooks4K(it.name) }
        .take(itemsPerRow)
        .takeIf { it.isNotEmpty() }
        ?.let { rows += VodBrowseRow("4k", "4K Series", series = it) }

    categories.filter { it != "All" }
        .sortedBy { it.lowercase() }
        .forEach { category ->
            val items = shows.filter {
                it.genre?.contains(category, ignoreCase = true) == true ||
                    it.categoryId.equals(category, ignoreCase = true)
            }.take(itemsPerRow)
            if (items.isNotEmpty()) {
                rows += VodBrowseRow("genre_$category", category, series = items)
            }
        }

    byCategory.entries
        .filter { (id, _) -> id.isNotBlank() }
        .sortedBy { (id, _) -> id.lowercase() }
        .forEach { (id, items) ->
            rows += VodBrowseRow("cat_$id", id, series = items.take(itemsPerRow))
        }

    return rows.filter { !it.isEmpty }.distinctBy { it.id }.take(maxRows)
}

private fun parseVodRating(item: VodItem): Double =
    item.rating?.trim()?.toDoubleOrNull() ?: 0.0

private fun titleLooks4K(title: String): Boolean {
    val upper = title.uppercase()
    return upper.contains("4K") || upper.contains("UHD") || upper.contains("2160P")
}
