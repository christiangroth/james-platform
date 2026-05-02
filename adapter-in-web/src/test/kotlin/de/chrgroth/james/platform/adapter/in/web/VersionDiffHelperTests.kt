package de.chrgroth.james.platform.adapter.`in`.web

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class VersionDiffHelperTests {

  @Test
  fun `diffLines returns empty list for two empty inputs`() {
    val result = VersionDiffHelper.diffLines(emptyList(), emptyList())
    assertThat(result).isEmpty()
  }

  @Test
  fun `diffLines marks all lines as added when old is empty`() {
    val result = VersionDiffHelper.diffLines(emptyList(), listOf("a", "b"))
    assertThat(result).containsExactly(
      DiffLine("a", DiffLineType.ADDED),
      DiffLine("b", DiffLineType.ADDED),
    )
  }

  @Test
  fun `diffLines marks all lines as removed when new is empty`() {
    val result = VersionDiffHelper.diffLines(listOf("a", "b"), emptyList())
    assertThat(result).containsExactly(
      DiffLine("a", DiffLineType.REMOVED),
      DiffLine("b", DiffLineType.REMOVED),
    )
  }

  @Test
  fun `diffLines marks all lines as unchanged for identical inputs`() {
    val lines = listOf("a", "b", "c")
    val result = VersionDiffHelper.diffLines(lines, lines)
    assertThat(result).containsExactly(
      DiffLine("a", DiffLineType.UNCHANGED),
      DiffLine("b", DiffLineType.UNCHANGED),
      DiffLine("c", DiffLineType.UNCHANGED),
    )
  }

  @Test
  fun `diffLines detects single line addition`() {
    val result = VersionDiffHelper.diffLines(listOf("a", "c"), listOf("a", "b", "c"))
    assertThat(result).containsExactly(
      DiffLine("a", DiffLineType.UNCHANGED),
      DiffLine("b", DiffLineType.ADDED),
      DiffLine("c", DiffLineType.UNCHANGED),
    )
  }

  @Test
  fun `diffLines detects single line removal`() {
    val result = VersionDiffHelper.diffLines(listOf("a", "b", "c"), listOf("a", "c"))
    assertThat(result).containsExactly(
      DiffLine("a", DiffLineType.UNCHANGED),
      DiffLine("b", DiffLineType.REMOVED),
      DiffLine("c", DiffLineType.UNCHANGED),
    )
  }

  @Test
  fun `diffLines handles completely different inputs`() {
    val result = VersionDiffHelper.diffLines(listOf("a", "b"), listOf("c", "d"))
    assertThat(result.filter { it.type == DiffLineType.REMOVED }.map { it.content }).containsExactly("a", "b")
    assertThat(result.filter { it.type == DiffLineType.ADDED }.map { it.content }).containsExactly("c", "d")
    assertThat(result.none { it.type == DiffLineType.UNCHANGED }).isTrue()
  }

  @Test
  fun `diffLines handles single-element change`() {
    val result = VersionDiffHelper.diffLines(listOf("old"), listOf("new"))
    assertThat(result.filter { it.type == DiffLineType.REMOVED }.map { it.content }).containsExactly("old")
    assertThat(result.filter { it.type == DiffLineType.ADDED }.map { it.content }).containsExactly("new")
  }

  @Test
  fun `diffLines preserves unchanged lines around modifications`() {
    val old = listOf("header", "old-line", "footer")
    val new = listOf("header", "new-line", "footer")
    val result = VersionDiffHelper.diffLines(old, new)
    assertThat(result.first()).isEqualTo(DiffLine("header", DiffLineType.UNCHANGED))
    assertThat(result.last()).isEqualTo(DiffLine("footer", DiffLineType.UNCHANGED))
    assertThat(result.filter { it.type == DiffLineType.REMOVED }.map { it.content }).containsExactly("old-line")
    assertThat(result.filter { it.type == DiffLineType.ADDED }.map { it.content }).containsExactly("new-line")
  }
}
