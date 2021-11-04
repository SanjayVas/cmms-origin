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

package org.wfanet.measurement.kingdom.deploy.gcloud.spanner.writers

import com.google.cloud.spanner.Value
import java.lang.IllegalStateException
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.singleOrNull
import org.wfanet.measurement.common.identity.ExternalId
import org.wfanet.measurement.common.identity.InternalId
import org.wfanet.measurement.gcloud.spanner.bufferUpdateMutation
import org.wfanet.measurement.gcloud.spanner.set
import org.wfanet.measurement.gcloud.spanner.setJson
import org.wfanet.measurement.internal.kingdom.Certificate
import org.wfanet.measurement.internal.kingdom.Measurement
import org.wfanet.measurement.internal.kingdom.MeasurementKt
import org.wfanet.measurement.internal.kingdom.RevokeCertificateRequest
import org.wfanet.measurement.internal.kingdom.StreamMeasurementsRequestKt
import org.wfanet.measurement.internal.kingdom.copy
import org.wfanet.measurement.kingdom.deploy.common.DuchyIds
import org.wfanet.measurement.kingdom.deploy.gcloud.spanner.common.KingdomInternalException
import org.wfanet.measurement.kingdom.deploy.gcloud.spanner.queries.StreamMeasurements
import org.wfanet.measurement.kingdom.deploy.gcloud.spanner.readers.BaseSpannerReader
import org.wfanet.measurement.kingdom.deploy.gcloud.spanner.readers.CertificateReader

/**
 * Revokes a certificate in the database.
 *
 * Throws a [KingdomInternalException] on [execute] with the following codes/conditions:
 * * [KingdomInternalException.Code.CERTIFICATE_NOT_FOUND]
 *
 * TODO(world-federation-of-advertisers/cross-media-measurement#305) : Consider failing all
 * associated active measurements if a DataProvider certificate or a Duchy certificate is revoked
 */
class RevokeCertificate(private val request: RevokeCertificateRequest) :
  SpannerWriter<Certificate, Certificate>() {

  override suspend fun TransactionScope.runTransaction(): Certificate {

    val externalCertificateId = ExternalId(request.externalCertificateId)
    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA") // Proto enum fields are never null.
    val reader: BaseSpannerReader<CertificateReader.Result> =
      when (request.parentCase) {
        RevokeCertificateRequest.ParentCase.EXTERNAL_DATA_PROVIDER_ID ->
          CertificateReader(CertificateReader.ParentType.DATA_PROVIDER)
            .bindWhereClause(ExternalId(request.externalDataProviderId), externalCertificateId)
        RevokeCertificateRequest.ParentCase.EXTERNAL_MEASUREMENT_CONSUMER_ID ->
          CertificateReader(CertificateReader.ParentType.MEASUREMENT_CONSUMER)
            .bindWhereClause(
              ExternalId(request.externalMeasurementConsumerId),
              externalCertificateId
            )
        RevokeCertificateRequest.ParentCase.EXTERNAL_MODEL_PROVIDER_ID ->
          CertificateReader(CertificateReader.ParentType.MODEL_PROVIDER)
            .bindWhereClause(ExternalId(request.externalModelProviderId), externalCertificateId)
        RevokeCertificateRequest.ParentCase.EXTERNAL_DUCHY_ID -> {
          val duchyId =
            InternalId(
              DuchyIds.getInternalId(request.externalDuchyId)
                ?: throw KingdomInternalException(KingdomInternalException.Code.DUCHY_NOT_FOUND) {
                  " Duchy not found."
                }
            )
          CertificateReader(CertificateReader.ParentType.DUCHY)
            .bindWhereClause(duchyId, externalCertificateId)
        }
        RevokeCertificateRequest.ParentCase.PARENT_NOT_SET ->
          throw IllegalStateException("RevokeCertificateRequest is missing parent field.")
      }

    val certificateResult =
      reader.execute(transactionContext).singleOrNull()
        ?: throw KingdomInternalException(KingdomInternalException.Code.CERTIFICATE_NOT_FOUND) {
          "Certificate not found."
        }

    transactionContext.bufferUpdateMutation("Certificates") {
      set("CertificateId" to certificateResult.certificateId.value)
      set("RevocationState" to request.revocationState)
    }

    if (request.parentCase == RevokeCertificateRequest.ParentCase.EXTERNAL_MEASUREMENT_CONSUMER_ID
    ) {
      val filter =
        StreamMeasurementsRequestKt.filter {
          externalMeasurementConsumerId = request.externalMeasurementConsumerId
          externalMeasurementConsumerCertificateId = request.externalCertificateId
          states += Measurement.State.PENDING_COMPUTATION
          states += Measurement.State.PENDING_PARTICIPANT_CONFIRMATION
          states += Measurement.State.PENDING_REQUISITION_FULFILLMENT
          states += Measurement.State.PENDING_REQUISITION_PARAMS
        }

      StreamMeasurements(Measurement.View.DEFAULT, filter).execute(transactionContext).collect {
        val details =
          it.measurement.details.copy {
            failure =
              MeasurementKt.failure {
                reason = Measurement.Failure.Reason.CERTIFICATE_REVOKED
                message = "The associated Measurement Consumer certificate has been revoked."
              }
          }
        transactionContext.bufferUpdateMutation("Measurements") {
          set("MeasurementConsumerId" to it.measurementConsumerId)
          set("MeasurementId" to it.measurementId)
          set("State" to Measurement.State.FAILED)
          set("UpdateTime" to Value.COMMIT_TIMESTAMP)
          set("MeasurementDetails" to details)
          setJson("MeasurementDetailsJson" to details)
        }
      }
    }

    return certificateResult.certificate.copy { revocationState = request.revocationState }
  }

  override fun ResultScope<Certificate>.buildResult(): Certificate {
    return checkNotNull(transactionResult)
  }
}
