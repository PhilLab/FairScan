/*
 * Copyright 2026 The FairScan authors
 *
 * This program is free software: you can redistribute it and/or modify it
 * under the terms of the GNU General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option)
 * any later version.
 * This program is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or
 * FITNESS FOR A PARTICULAR PURPOSE. See the GNU General Public License for
 * more details.
 * You should have received a copy of the GNU General Public License along with
 * this program. If not, see <https://www.gnu.org/licenses/>.
 */
package org.fairscan.app

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class AppContainerTest {

    @get:Rule
    var folder: TemporaryFolder = TemporaryFolder()

    @Test
    fun resolveStorageSubdir_throws_when_not_configured() {
        assertThatThrownBy { resolveStorageSubdir(null) }
            .isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun resolveStorageSubdir_passes_through_a_configured_value() {
        assertThat(resolveStorageSubdir("")).isEqualTo("")
        assertThat(resolveStorageSubdir("fairscan")).isEqualTo("fairscan")
    }

    @Test
    fun empty_subdir_means_use_the_dir_directly() {
        val dir = folder.newFolder()
        assertThat(storageRoot(dir, "")).isEqualTo(dir)
    }

    @Test
    fun non_empty_subdir_nests_under_the_dir() {
        val dir = folder.newFolder()
        assertThat(storageRoot(dir, "fairscan")).isEqualTo(File(dir, "fairscan"))
    }
}
