package com.grid.tv.domain.model

import org.junit.Assert.assertEquals
import org.junit.Test

class VodCategoryGenreHintMergeTest {

  @Test
  fun movieGenreHint_resolvesNumericCategoryId() {
    val lookup = VodCategoryNameResolver.mergeLookupTables(
      mapOf("1_1006" to "Action"),
      mapOf("1006" to "Action")
    )
    assertEquals(
      "Action",
      VodCategoryNameResolver.resolveDisplayName(
        categoryId = "1006",
        storedName = "1006",
        playlistId = 1L,
        lookupById = lookup
      )
    )
  }

  @Test
  fun movieGenreHint_doesNotReplaceHumanReadableName() {
    val lookup = mapOf(
      "1_1006" to "NETFLIX ASIA",
      "1006" to "Drama"
    )
    assertEquals(
      "NETFLIX ASIA",
      VodCategoryNameResolver.resolveDisplayName(
        categoryId = "1006",
        storedName = "1006",
        playlistId = 1L,
        lookupById = lookup
      )
    )
  }
}
