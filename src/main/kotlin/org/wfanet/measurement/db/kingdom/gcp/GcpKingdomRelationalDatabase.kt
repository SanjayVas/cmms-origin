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

package org.wfanet.measurement.db.kingdom.gcp

import java.time.Clock
import kotlinx.coroutines.flow.Flow
import org.wfanet.measurement.common.identity.ExternalId
import org.wfanet.measurement.common.identity.IdGenerator
import org.wfanet.measurement.db.gcp.AsyncDatabaseClient
import org.wfanet.measurement.db.kingdom.KingdomRelationalDatabase
import org.wfanet.measurement.db.kingdom.StreamReportsFilter
import org.wfanet.measurement.db.kingdom.StreamRequisitionsFilter
import org.wfanet.measurement.db.kingdom.gcp.queries.GetReport
import org.wfanet.measurement.db.kingdom.gcp.queries.ReadRequisitionTemplates
import org.wfanet.measurement.db.kingdom.gcp.queries.SpannerQuery
import org.wfanet.measurement.db.kingdom.gcp.queries.StreamReadyReports
import org.wfanet.measurement.db.kingdom.gcp.queries.StreamReadySchedules
import org.wfanet.measurement.db.kingdom.gcp.queries.StreamReports
import org.wfanet.measurement.db.kingdom.gcp.queries.StreamRequisitions
import org.wfanet.measurement.db.kingdom.gcp.writers.AssociateRequisitionAndReport
import org.wfanet.measurement.db.kingdom.gcp.writers.ConfirmDuchyReadiness
import org.wfanet.measurement.db.kingdom.gcp.writers.CreateAdvertiser
import org.wfanet.measurement.db.kingdom.gcp.writers.CreateCampaign
import org.wfanet.measurement.db.kingdom.gcp.writers.CreateDataProvider
import org.wfanet.measurement.db.kingdom.gcp.writers.CreateNextReport
import org.wfanet.measurement.db.kingdom.gcp.writers.CreateReportConfig
import org.wfanet.measurement.db.kingdom.gcp.writers.CreateReportLogEntry
import org.wfanet.measurement.db.kingdom.gcp.writers.CreateRequisition
import org.wfanet.measurement.db.kingdom.gcp.writers.CreateSchedule
import org.wfanet.measurement.db.kingdom.gcp.writers.FinishReport
import org.wfanet.measurement.db.kingdom.gcp.writers.FulfillRequisition
import org.wfanet.measurement.db.kingdom.gcp.writers.SpannerWriter
import org.wfanet.measurement.db.kingdom.gcp.writers.UpdateReportState
import org.wfanet.measurement.internal.kingdom.Advertiser
import org.wfanet.measurement.internal.kingdom.Campaign
import org.wfanet.measurement.internal.kingdom.DataProvider
import org.wfanet.measurement.internal.kingdom.Report
import org.wfanet.measurement.internal.kingdom.Report.ReportState
import org.wfanet.measurement.internal.kingdom.ReportConfig
import org.wfanet.measurement.internal.kingdom.ReportConfigSchedule
import org.wfanet.measurement.internal.kingdom.ReportDetails
import org.wfanet.measurement.internal.kingdom.ReportLogEntry
import org.wfanet.measurement.internal.kingdom.Requisition
import org.wfanet.measurement.internal.kingdom.RequisitionTemplate

class GcpKingdomRelationalDatabase(
  private val clock: Clock,
  private val idGenerator: IdGenerator,
  private val client: AsyncDatabaseClient
) : KingdomRelationalDatabase {

  override suspend fun createRequisition(requisition: Requisition): Requisition {
    return CreateRequisition(requisition).execute()
  }

  override suspend fun fulfillRequisition(
    externalRequisitionId: ExternalId,
    duchyId: String
  ): Requisition {
    return FulfillRequisition(externalRequisitionId, duchyId).execute()
  }

  override fun streamRequisitions(
    filter: StreamRequisitionsFilter,
    limit: Long
  ): Flow<Requisition> {
    return StreamRequisitions(filter, limit).execute()
  }

  override suspend fun getReport(externalId: ExternalId): Report {
    return GetReport(externalId).executeSingle()
  }

  override suspend fun createNextReport(
    externalScheduleId: ExternalId,
    combinedPublicKeyResourceId: String
  ): Report {
    return CreateNextReport(externalScheduleId, combinedPublicKeyResourceId).execute()
  }

  override suspend fun updateReportState(externalReportId: ExternalId, state: ReportState): Report {
    return UpdateReportState(externalReportId, state).execute()
  }

  override fun streamReports(filter: StreamReportsFilter, limit: Long): Flow<Report> {
    return StreamReports(filter, limit).execute()
  }

  override fun streamReadyReports(limit: Long): Flow<Report> {
    return StreamReadyReports(limit).execute()
  }

  override suspend fun associateRequisitionToReport(
    externalRequisitionId: ExternalId,
    externalReportId: ExternalId
  ) {
    AssociateRequisitionAndReport(externalRequisitionId, externalReportId).execute()
  }

  override fun listRequisitionTemplates(reportConfigId: ExternalId): Flow<RequisitionTemplate> {
    return ReadRequisitionTemplates(reportConfigId).execute()
  }

  override fun streamReadySchedules(limit: Long): Flow<ReportConfigSchedule> {
    return StreamReadySchedules(limit).execute()
  }

  override suspend fun addReportLogEntry(reportLogEntry: ReportLogEntry): ReportLogEntry {
    return CreateReportLogEntry(reportLogEntry).execute()
  }

  override suspend fun confirmDuchyReadiness(
    externalReportId: ExternalId,
    duchyId: String,
    externalRequisitionIds: Set<ExternalId>
  ): Report {
    return ConfirmDuchyReadiness(externalReportId, duchyId, externalRequisitionIds).execute()
  }

  override suspend fun finishReport(
    externalReportId: ExternalId,
    result: ReportDetails.Result
  ): Report {
    return FinishReport(externalReportId, result).execute()
  }

  override suspend fun createDataProvider(): DataProvider {
    return CreateDataProvider().execute()
  }

  override suspend fun createAdvertiser(): Advertiser {
    return CreateAdvertiser().execute()
  }

  override suspend fun createCampaign(
    externalDataProviderId: ExternalId,
    externalAdvertiserId: ExternalId,
    providedCampaignId: String
  ): Campaign {
    return CreateCampaign(externalDataProviderId, externalAdvertiserId, providedCampaignId)
      .execute()
  }

  override suspend fun createReportConfig(
    reportConfig: ReportConfig,
    campaigns: List<ExternalId>
  ): ReportConfig {
    return CreateReportConfig(reportConfig, campaigns).execute()
  }

  override suspend fun createSchedule(schedule: ReportConfigSchedule): ReportConfigSchedule {
    return CreateSchedule(schedule).execute()
  }

  // Convenience functions for executing reads and writes.
  private suspend fun <R> SpannerWriter<*, R>.execute(): R = execute(client, idGenerator, clock)
  private fun <R> SpannerQuery<*, R>.execute(): Flow<R> = execute(client.singleUse())
  private suspend fun <R> SpannerQuery<*, R>.executeSingle(): R = executeSingle(client.singleUse())
}
