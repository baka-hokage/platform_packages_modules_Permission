/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.safetycenter.persistence

import android.os.Build.VERSION_CODES.TIRAMISU
import androidx.test.filters.SdkSuppress
import com.android.safetycenter.persistence.PersistenceConstants.PATH
import com.google.common.truth.Truth.assertThat
import java.io.File
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
@SdkSuppress(minSdkVersion = TIRAMISU, codeName = "Tiramisu")
class SafetyCenterIssuesPersistenceInvalidTest {

    data class Params(
        private val testName: String,
        val fileName: String,
        val errorMessage: String,
        val causeErrorMessage: String?
    ) {
        override fun toString() = testName
    }

    @Parameterized.Parameter lateinit var params: Params

    @Test
    fun invalidFile_throws() {
        val file = File(PATH + params.fileName)

        val thrown =
            assertThrows(PersistenceException::class.java) {
                SafetyCenterIssuesPersistence.readForUser(file)
            }

        assertThat(thrown).hasMessageThat().isEqualTo(params.errorMessage)
        if (params.causeErrorMessage != null) {
            assertThat(thrown.cause).hasMessageThat().isEqualTo(params.causeErrorMessage)
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "{0}")
        fun parameters() =
            arrayOf(
                Params(
                    "Corrupted",
                    "invalid_file_corrupted.txt",
                    "Failed to read file: ${PATH}invalid_file_corrupted.txt",
                    null),
                Params(
                    "ExtraAttribute",
                    "invalid_file_extra_attribute.xml",
                    "Unexpected attribute extra",
                    null),
                Params(
                    "ExtraElement",
                    "invalid_file_extra_element.xml",
                    "Element issue not closed",
                    null),
                Params(
                    "ExtraRoot",
                    "invalid_file_extra_root.txt",
                    "Unexpected extra root element",
                    null),
                Params(
                    "InvalidDismissedAt",
                    "invalid_file_invalid_dismissed_at.xml",
                    "Attribute value \"NaN\" for dismissed_at_epoch_millis invalid",
                    null),
                Params(
                    "InvalidFirstSeenAt",
                    "invalid_file_invalid_first_seen_at.xml",
                    "Attribute value \"NaN\" for first_seen_at_epoch_millis invalid",
                    null),
                Params(
                    "InvalidVersion",
                    "invalid_file_invalid_version.xml",
                    "Attribute value \"NaN\" for version invalid",
                    null),
                Params(
                    "MissingFirstSeenAt",
                    "invalid_file_missing_first_seen_at.xml",
                    "Element issue invalid",
                    "Required attribute first seen at missing"),
                Params(
                    "MissingIssueId",
                    "invalid_file_missing_issue_id.xml",
                    "Element issue invalid",
                    "Required attribute issue id missing"),
                Params(
                    "MissingSourceId",
                    "invalid_file_missing_source_id.xml",
                    "Element issue invalid",
                    "Required attribute source id missing"),
                Params(
                    "MissingVersion",
                    "invalid_file_missing_version.xml",
                    "Missing version",
                    null),
                Params("WrongRoot", "invalid_file_wrong_root.xml", "Element issues missing", null),
                Params(
                    "WrongVersion",
                    "invalid_file_wrong_version.xml",
                    "Unsupported version: 99",
                    null))
    }
}