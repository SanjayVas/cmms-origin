// Copyright 2021 The Cross-Media Measurement Authors
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

package org.wfanet.measurement.kingdom.deploy.gcloud.spanner.readers

import com.google.cloud.spanner.Key
import com.google.cloud.spanner.Struct
import org.wfanet.measurement.common.identity.ExternalId
import org.wfanet.measurement.common.identity.InternalId
import org.wfanet.measurement.common.singleOrNullIfEmpty
import org.wfanet.measurement.gcloud.spanner.AsyncDatabaseClient
import org.wfanet.measurement.gcloud.spanner.appendClause
import org.wfanet.measurement.gcloud.spanner.getInternalId
import org.wfanet.measurement.gcloud.spanner.getProtoMessage
import org.wfanet.measurement.internal.kingdom.DataProvider
import org.wfanet.measurement.kingdom.deploy.common.DuchyIds

class DataProviderReader : SpannerReader<DataProviderReader.Result>() {
  data class Result(val dataProvider: DataProvider, val dataProviderId: Long)

  override val baseSql: String =
    """
    SELECT
      DataProviders.DataProviderId,
      DataProviders.ExternalDataProviderId,
      DataProviders.DataProviderDetails,
      DataProviders.DataProviderDetailsJson,
      DataProviderCertificates.ExternalDataProviderCertificateId,
      Certificates.SubjectKeyIdentifier,
      Certificates.NotValidBefore,
      Certificates.NotValidAfter,
      Certificates.RevocationState,
      Certificates.CertificateDetails,
      ARRAY(
        SELECT AS STRUCT
          DataProviderRequiredDuchies.DuchyId
        FROM
          DataProviders
          JOIN DataProviderRequiredDuchies USING (DataProviderId)
      ) AS DataProviderRequiredDuchies,
    FROM DataProviders
    JOIN DataProviderCertificates ON (
      DataProviderCertificates.DataProviderId = DataProviders.DataProviderId
      AND DataProviderCertificates.CertificateId = DataProviders.PublicKeyCertificateId
    )
    JOIN Certificates USING (CertificateId)
    """
      .trimIndent()

  override suspend fun translate(struct: Struct): Result =
    Result(buildDataProvider(struct), struct.getLong("DataProviderId"))

  suspend fun readByExternalDataProviderId(
    readContext: AsyncDatabaseClient.ReadContext,
    externalDataProviderId: ExternalId,
  ): Result? {
    return fillStatementBuilder {
        appendClause("WHERE ExternalDataProviderId = @externalDataProviderId")
        bind("externalDataProviderId").to(externalDataProviderId.value)
      }
      .execute(readContext)
      .singleOrNullIfEmpty()
  }

  private fun buildDataProvider(struct: Struct): DataProvider =
    DataProvider.newBuilder()
      .apply {
        externalDataProviderId = struct.getLong("ExternalDataProviderId")
        details = struct.getProtoMessage("DataProviderDetails", DataProvider.Details.parser())
        certificate = CertificateReader.buildDataProviderCertificate(struct)
        addAllRequiredExternalDuchyIds(buildExternalDuchyIdList(struct))
      }
      .build()

  private fun buildExternalDuchyIdList(struct: Struct): List<String> {
    return struct.getStructList("DataProviderRequiredDuchies").map {
      checkNotNull(DuchyIds.getExternalId(it.getLong("DuchyId"))) {
        "Duchy with internal ID ${it.getLong("DuchyId")} not found"
      }
    }
  }

  companion object {
    /** Reads the [InternalId] for a DataProvider given its [ExternalId]. */
    suspend fun readDataProviderId(
      readContext: AsyncDatabaseClient.ReadContext,
      externalDataProviderId: ExternalId,
    ): InternalId? {
      val column = "DataProviderId"
      val row: Struct =
        readContext.readRowUsingIndex(
          "DataProviders",
          "DataProvidersByExternalId",
          Key.of(externalDataProviderId.value),
          column,
        ) ?: return null

      return row.getInternalId(column)
    }
  }
}
