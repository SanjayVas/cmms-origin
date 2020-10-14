// Copyright 2020 The Measurement System Authors
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

package org.wfanet.measurement.integration

import com.google.cloud.storage.contrib.nio.testing.LocalStorageHelper
import java.time.Duration
import org.junit.runner.Description
import org.junit.runners.model.Statement
import org.wfanet.measurement.common.Duchy
import org.wfanet.measurement.common.crypto.ElGamalKeyPair
import org.wfanet.measurement.common.crypto.toProtoMessage
import org.wfanet.measurement.common.identity.RandomIdGenerator
import org.wfanet.measurement.common.testing.ProviderRule
import org.wfanet.measurement.common.testing.chainRulesSequentially
import org.wfanet.measurement.db.duchy.computation.ComputationsRelationalDb
import org.wfanet.measurement.db.duchy.computation.LiquidLegionsSketchAggregationProtocol
import org.wfanet.measurement.db.duchy.computation.ProtocolStageEnumHelper
import org.wfanet.measurement.db.duchy.computation.ReadOnlyComputationsRelationalDb
import org.wfanet.measurement.db.duchy.computation.SingleProtocolDatabase
import org.wfanet.measurement.db.duchy.computation.gcp.ComputationMutations
import org.wfanet.measurement.db.duchy.computation.gcp.GcpSpannerComputationsDb
import org.wfanet.measurement.db.duchy.computation.gcp.GcpSpannerReadOnlyComputationsRelationalDb
import org.wfanet.measurement.db.duchy.metricvalue.MetricValueDatabase
import org.wfanet.measurement.db.duchy.metricvalue.gcp.SpannerMetricValueDatabase
import org.wfanet.measurement.db.gcp.AsyncDatabaseClient
import org.wfanet.measurement.db.gcp.testing.SpannerEmulatorDatabaseRule
import org.wfanet.measurement.duchy.mill.CryptoKeySet
import org.wfanet.measurement.duchy.testing.DUCHY_PUBLIC_KEYS
import org.wfanet.measurement.duchy.testing.DUCHY_SECRET_KEYS
import org.wfanet.measurement.internal.duchy.ComputationStage
import org.wfanet.measurement.internal.duchy.ComputationStageDetails
import org.wfanet.measurement.internal.duchy.ComputationTypeEnum
import org.wfanet.measurement.storage.StorageClient
import org.wfanet.measurement.storage.gcs.GcsStorageClient

private const val METRIC_VALUE_SCHEMA_RESOURCE_PATH = "/src/main/db/gcp/metric_values.sdl"
private const val COMPUTATIONS_SCHEMA_RESOURCE_PATH = "/src/main/db/gcp/computations.sdl"

class GcpDuchyDependencyProviderRule(
  duchyIds: Iterable<String>
) : ProviderRule<(Duchy) -> InProcessDuchy.DuchyDependencies> {
  override val value: (Duchy) -> InProcessDuchy.DuchyDependencies = this::buildDuchyDependencies

  private val metricValueDatabaseRules =
    duchyIds
      .map { it to SpannerEmulatorDatabaseRule(METRIC_VALUE_SCHEMA_RESOURCE_PATH) }
      .toMap()

  private val computationsDatabaseRules =
    duchyIds
      .map { it to SpannerEmulatorDatabaseRule(COMPUTATIONS_SCHEMA_RESOURCE_PATH) }
      .toMap()

  override fun apply(base: Statement, description: Description): Statement {
    val rules = metricValueDatabaseRules.values + computationsDatabaseRules.values
    return chainRulesSequentially(rules).apply(base, description)
  }

  private fun buildDuchyDependencies(duchy: Duchy): InProcessDuchy.DuchyDependencies {
    val metricValueDatabase = metricValueDatabaseRules[duchy.name]
      ?: error("Missing MetricValue Spanner database for duchy $duchy")
    val computationsDatabase = computationsDatabaseRules[duchy.name]
      ?: error("Missing Computations Spanner database for duchy $duchy")

    return InProcessDuchy.DuchyDependencies(
      buildSingleProtocolDb(duchy.name, computationsDatabase.databaseClient),
      buildMetricValueDb(metricValueDatabase.databaseClient),
      buildStorageClient(duchy.name),
      DUCHY_PUBLIC_KEYS,
      buildCryptoKeySet(duchy.name)
    )
  }

  private fun buildSingleProtocolDb(
    duchyId: String,
    computationsDatabaseClient: AsyncDatabaseClient
  ): SingleProtocolDatabase {
    val otherDuchyNames = (DUCHY_IDS.toSet() - duchyId).toList()
    val stageEnumHelper = LiquidLegionsSketchAggregationProtocol.ComputationStages
    val stageDetails =
      LiquidLegionsSketchAggregationProtocol.ComputationStages.Details(otherDuchyNames)
    val readOnlyDb = GcpSpannerReadOnlyComputationsRelationalDb(
      computationsDatabaseClient,
      stageEnumHelper
    )
    val computationsDb: ComputationsRelationalDb<ComputationStage, ComputationStageDetails> =
      GcpSpannerComputationsDb(
        databaseClient = computationsDatabaseClient,
        duchyName = duchyId,
        duchyOrder = DUCHY_ORDER,
        blobStorageBucket = "mill-computation-stage-storage-$duchyId",
        computationMutations = ComputationMutations(stageEnumHelper, stageDetails),
        lockDuration = Duration.ofSeconds(1)
      )

    return object :
      SingleProtocolDatabase,
      ReadOnlyComputationsRelationalDb by readOnlyDb,
      ComputationsRelationalDb<ComputationStage, ComputationStageDetails> by computationsDb,
      ProtocolStageEnumHelper<ComputationStage> by stageEnumHelper {
      override val computationType =
        ComputationTypeEnum.ComputationType.LIQUID_LEGIONS_SKETCH_AGGREGATION_V1
    }
  }

  private fun buildMetricValueDb(databaseClient: AsyncDatabaseClient): MetricValueDatabase {
    return SpannerMetricValueDatabase(databaseClient, RandomIdGenerator())
  }

  private fun buildStorageClient(duchyId: String): StorageClient {
    return GcsStorageClient(LocalStorageHelper.getOptions().service, "bucket-$duchyId")
  }

  private fun buildCryptoKeySet(duchyId: String): CryptoKeySet {
    val latestDuchyPublicKeys = DUCHY_PUBLIC_KEYS.latest
    val keyPair =
      ElGamalKeyPair(
        latestDuchyPublicKeys.getValue(duchyId),
        checkNotNull(DUCHY_SECRET_KEYS[duchyId]) { "Secret key not found for $duchyId" }
      )
    return CryptoKeySet(
      ownPublicAndPrivateKeys = keyPair.toProtoMessage(),
      otherDuchyPublicKeys = latestDuchyPublicKeys.mapValues { it.value.toProtoMessage() },
      clientPublicKey = latestDuchyPublicKeys.combinedPublicKey.toProtoMessage(),
      curveId = latestDuchyPublicKeys.combinedPublicKey.ellipticCurveId
    )
  }
}
