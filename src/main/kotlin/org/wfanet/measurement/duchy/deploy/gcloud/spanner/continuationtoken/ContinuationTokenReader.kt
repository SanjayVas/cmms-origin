// Copyright 2022 The Cross-Media Measurement Authors
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package org.wfanet.measurement.duchy.deploy.gcloud.spanner.continuationtoken

import com.google.cloud.spanner.Statement
import com.google.cloud.spanner.Struct
import org.wfanet.measurement.duchy.deploy.gcloud.spanner.common.SqlBasedQuery

class ContinuationTokenReader() : SqlBasedQuery<ContinuationTokenReaderResult> {
  companion object {
    private const val parameterizedQueryString =
      """
      SELECT ContinuationToken
      FROM HeraldContinuationTokens
      Limit 1
    """
  }

  override val sql: Statement = Statement.newBuilder(parameterizedQueryString).build()

  override fun asResult(struct: Struct): ContinuationTokenReaderResult =
    ContinuationTokenReaderResult(continuationToken = struct.getString("ContinuationToken"))
}

/** @see [SpannerContinuationTokenQuery.asResult] . */
data class ContinuationTokenReaderResult(val continuationToken: String)
