/*
 * Copyright 2023 The Cross-Media Measurement Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.wfanet.measurement.reporting.deploy.v2.postgres.readers

import com.google.protobuf.Timestamp
import com.google.protobuf.util.Timestamps
import java.time.Instant
import java.time.ZoneOffset
import java.util.Optional
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flow
import org.wfanet.measurement.common.db.r2dbc.BoundStatement
import org.wfanet.measurement.common.db.r2dbc.ReadContext
import org.wfanet.measurement.common.db.r2dbc.ResultRow
import org.wfanet.measurement.common.db.r2dbc.boundStatement
import org.wfanet.measurement.common.identity.ExternalId
import org.wfanet.measurement.common.identity.InternalId
import org.wfanet.measurement.common.toInstant
import org.wfanet.measurement.common.toProtoTime
import org.wfanet.measurement.internal.reporting.v2.Report
import org.wfanet.measurement.internal.reporting.v2.ReportKt
import org.wfanet.measurement.internal.reporting.v2.StreamReportsRequest
import org.wfanet.measurement.internal.reporting.v2.TimeInterval
import org.wfanet.measurement.internal.reporting.v2.periodicTimeInterval
import org.wfanet.measurement.internal.reporting.v2.report
import org.wfanet.measurement.internal.reporting.v2.timeInterval
import org.wfanet.measurement.internal.reporting.v2.timeIntervals

class ReportReader(private val readContext: ReadContext) {
  data class Result(
    val measurementConsumerId: InternalId,
    val reportId: InternalId,
    val createReportRequestId: String,
    val report: Report
  )

  private data class ReportInfo(
    val measurementConsumerId: InternalId,
    val cmmsMeasurementConsumerId: String,
    val createReportRequestId: String?,
    val reportId: InternalId,
    val externalReportId: ExternalId,
    val createTime: Timestamp,
    val timeIntervals: MutableSet<TimeInterval>,
    /** Map of external reporting set ID to [ReportingMetricCalculationSpecInfo]. */
    val reportingSetReportingMetricCalculationSpecInfoMap:
      MutableMap<ExternalId, ReportingMetricCalculationSpecInfo>,
  )

  private data class ReportingMetricCalculationSpecInfo(
    val metricCalculationSpecInfoMap:
      MutableMap<Report.MetricCalculationSpec.Details, MetricCalculationSpecInfo>,
  )

  private data class MetricCalculationSpecInfo(
    // Key is createMetricRequestId.
    val reportingMetricMap: MutableMap<String, Report.ReportingMetric>,
  )

  private val baseSqlSelect: String =
    """
    SELECT
      CmmsMeasurementConsumerId,
      Reports.MeasurementConsumerId,
      Reports.ReportId,
      Reports.ExternalReportId,
      Reports.CreateReportRequestId,
      Reports.CreateTime,
      ReportTimeIntervals.TimeIntervalStart,
      ReportTimeIntervals.TimeIntervalEndExclusive,
      MetricCalculationSpecs.MetricCalculationSpecDetails,
      ReportingSets.ExternalReportingSetId,
      MetricCalculationSpecReportingMetrics.CreateMetricRequestId,
      MetricCalculationSpecReportingMetrics.ReportingMetricDetails,
      Metrics.ExternalMetricId
    """
      .trimIndent()

  private val baseSqlJoins: String =
    """
    JOIN ReportTimeIntervals USING(MeasurementConsumerId, ReportId)
    JOIN MetricCalculationSpecs USING(MeasurementConsumerId, ReportId)
    JOIN ReportingSets USING(MeasurementConsumerId, ReportingSetId)
    JOIN MetricCalculationSpecReportingMetrics USING(MeasurementConsumerId, ReportId, MetricCalculationSpecId)
    LEFT JOIN Metrics USING(MeasurementConsumerId, MetricId)
    """
      .trimIndent()

  suspend fun readReportByRequestId(
    measurementConsumerId: InternalId,
    createReportRequestId: String,
  ): Result? {
    val sql =
      StringBuilder(
        baseSqlSelect +
          "\n" +
          """
        FROM MeasurementConsumers
          JOIN Reports USING(MeasurementConsumerId)
        """
            .trimIndent() +
          "\n" +
          baseSqlJoins +
          "\n" +
          """
        WHERE Reports.MeasurementConsumerId = $1
          AND CreateReportRequestId = $2
        """
            .trimIndent()
      )

    val statement =
      boundStatement(sql.toString()) {
        bind("$1", measurementConsumerId)
        bind("$2", createReportRequestId)
      }

    return createResultFlow(statement).firstOrNull()
  }

  suspend fun readReportByExternalId(
    cmmsMeasurementConsumerId: String,
    externalReportId: ExternalId,
  ): Result? {
    val sql =
      StringBuilder(
        baseSqlSelect +
          "\n" +
          """
        FROM MeasurementConsumers
          JOIN Reports USING(MeasurementConsumerId)
        """
            .trimIndent() +
          "\n" +
          baseSqlJoins +
          "\n" +
          """
        WHERE CmmsMeasurementConsumerId = $1
          AND ExternalReportId = $2
        """
            .trimIndent()
      )

    val statement =
      boundStatement(sql.toString()) {
        bind("$1", cmmsMeasurementConsumerId)
        bind("$2", externalReportId)
      }

    return createResultFlow(statement).firstOrNull()
  }

  fun readReports(
    request: StreamReportsRequest,
  ): Flow<Result> {
    val sql =
      StringBuilder(
        baseSqlSelect +
          "\n" +
          """
        FROM (
          SELECT *
          FROM MeasurementConsumers
            JOIN Reports USING (MeasurementConsumerId)
          WHERE CmmsMeasurementConsumerId = $1
            AND ((CreateTime > $2) OR
            (CreateTime = $2
            AND ExternalReportId > $3))
          ORDER BY CreateTime ASC, CmmsMeasurementConsumerId ASC, ExternalReportId ASC
          LIMIT $4
        ) AS Reports
      """
            .trimIndent() +
          "\n" +
          baseSqlJoins +
          "\n" +
          """
          ORDER BY CreateTime ASC, CmmsMeasurementConsumerId ASC, ExternalReportId ASC
          """
            .trimIndent()
      )
    val statement =
      boundStatement(sql.toString()) {
        bind("$1", request.filter.cmmsMeasurementConsumerId)
        bind("$2", request.filter.after.createTime.toInstant().atOffset((ZoneOffset.UTC)))

        if (
          request.filter.after.createTime != Timestamp.getDefaultInstance() &&
            request.filter.after.externalReportId == 0L
        ) {
          bind("$3", Long.MAX_VALUE)
        } else {
          bind("$3", request.filter.after.externalReportId)
        }

        if (request.limit > 0) {
          bind("$4", request.limit)
        } else {
          bind("$4", 50)
        }
      }

    return createResultFlow(statement)
  }

  private fun createResultFlow(statement: BoundStatement): Flow<Result> {
    var accumulator: ReportInfo? = null
    val translate: (row: ResultRow) -> Optional<Result> = { row: ResultRow ->
      val measurementConsumerId: InternalId = row["MeasurementConsumerId"]
      val cmmsMeasurementConsumerId: String = row["CmmsMeasurementConsumerId"]
      val createReportRequestId: String? = row["CreateReportRequestId"]
      val reportId: InternalId = row["ReportId"]
      val externalReportId: ExternalId = row["ExternalReportId"]
      val createTime: Instant = row["CreateTime"]

      var result: Result? = null
      if (accumulator == null) {
        accumulator =
          ReportInfo(
            measurementConsumerId = measurementConsumerId,
            cmmsMeasurementConsumerId = cmmsMeasurementConsumerId,
            createReportRequestId = createReportRequestId,
            reportId = reportId,
            externalReportId = externalReportId,
            createTime = createTime.toProtoTime(),
            timeIntervals = mutableSetOf(),
            reportingSetReportingMetricCalculationSpecInfoMap = mutableMapOf()
          )
      } else if (
        accumulator!!.externalReportId != externalReportId ||
          accumulator!!.measurementConsumerId != measurementConsumerId
      ) {
        result = accumulator!!.toResult()
        accumulator =
          ReportInfo(
            measurementConsumerId = measurementConsumerId,
            cmmsMeasurementConsumerId = cmmsMeasurementConsumerId,
            createReportRequestId = createReportRequestId,
            reportId = reportId,
            externalReportId = externalReportId,
            createTime = createTime.toProtoTime(),
            timeIntervals = mutableSetOf(),
            reportingSetReportingMetricCalculationSpecInfoMap = mutableMapOf()
          )
      }

      accumulator!!.update(row)

      Optional.ofNullable(result)
    }

    return flow {
      // TODO(@tristanvuong2021): add null support to consume
      readContext.executeQuery(statement).consume(translate).collect {
        if (it.isPresent) {
          emit(it.get())
        }
      }
      if (accumulator != null) {
        emit(accumulator!!.toResult())
      }
    }
  }

  private fun ReportInfo.update(row: ResultRow) {
    val timeIntervalStart: Instant = row["TimeIntervalStart"]
    val timeIntervalEnd: Instant = row["TimeIntervalEndExclusive"]
    val metricCalculationSpecDetails: Report.MetricCalculationSpec.Details =
      row.getProtoMessage(
        "MetricCalculationSpecDetails",
        Report.MetricCalculationSpec.Details.parser()
      )
    val externalReportingSetId: ExternalId = row["ExternalReportingSetId"]
    val createMetricRequestId: String = row["CreateMetricRequestId"]
    val reportingMetricDetails: Report.ReportingMetric.Details =
      row.getProtoMessage("ReportingMetricDetails", Report.ReportingMetric.Details.parser())
    val externalMetricId: ExternalId? = row["ExternalMetricId"]

    val source = this

    source.timeIntervals.add(
      timeInterval {
        startTime = timeIntervalStart.toProtoTime()
        endTime = timeIntervalEnd.toProtoTime()
      }
    )

    val reportingMetricCalculationSpecInfo =
      source.reportingSetReportingMetricCalculationSpecInfoMap.computeIfAbsent(
        externalReportingSetId
      ) {
        ReportingMetricCalculationSpecInfo(
          metricCalculationSpecInfoMap = mutableMapOf(),
        )
      }

    val metricCalculationSpecInfo =
      reportingMetricCalculationSpecInfo.metricCalculationSpecInfoMap.computeIfAbsent(
        metricCalculationSpecDetails
      ) {
        MetricCalculationSpecInfo(
          reportingMetricMap = mutableMapOf(),
        )
      }

    metricCalculationSpecInfo.reportingMetricMap.computeIfAbsent(createMetricRequestId) {
      ReportKt.reportingMetric {
        this.createMetricRequestId = createMetricRequestId
        if (externalMetricId != null) {
          this.externalMetricId = externalMetricId.value
        }
        details = reportingMetricDetails
      }
    }
  }

  private fun ReportInfo.toResult(): Result {
    val source = this
    val report = report {
      cmmsMeasurementConsumerId = source.cmmsMeasurementConsumerId
      externalReportId = source.externalReportId.value
      createTime = source.createTime

      source.reportingSetReportingMetricCalculationSpecInfoMap.entries.forEach {
        reportingMetricEntry ->
        val reportingMetricCalculationSpec =
          ReportKt.reportingMetricCalculationSpec {
            reportingMetricEntry.value.metricCalculationSpecInfoMap.entries.forEach {
              metricCalculationSpecEntry ->
              metricCalculationSpecs +=
                ReportKt.metricCalculationSpec {
                  reportingMetrics += metricCalculationSpecEntry.value.reportingMetricMap.values
                  details = metricCalculationSpecEntry.key
                }
            }
          }

        reportingMetricEntries.put(reportingMetricEntry.key.value, reportingMetricCalculationSpec)
      }

      val sortedTimeIntervals =
        source.timeIntervals.sortedWith { a, b -> Timestamps.compare(a.startTime, b.startTime) }

      var isPeriodic = true
      for (i in 0..sortedTimeIntervals.size - 2) {
        if (sortedTimeIntervals[i].endTime != sortedTimeIntervals[i + 1].startTime) {
          isPeriodic = false
          break
        }
      }

      if (isPeriodic) {
        val firstTimeInterval = sortedTimeIntervals[0]
        periodicTimeInterval = periodicTimeInterval {
          startTime = firstTimeInterval.startTime
          increment = Timestamps.between(firstTimeInterval.startTime, firstTimeInterval.endTime)
          intervalCount = sortedTimeIntervals.size
        }
      } else {
        timeIntervals = timeIntervals {
          sortedTimeIntervals.forEach {
            timeIntervals += timeInterval {
              startTime = it.startTime
              endTime = it.endTime
            }
          }
        }
      }
    }

    val createReportRequestId = source.createReportRequestId ?: ""
    return Result(
      measurementConsumerId = source.measurementConsumerId,
      reportId = source.reportId,
      createReportRequestId = createReportRequestId,
      report = report,
    )
  }
}
