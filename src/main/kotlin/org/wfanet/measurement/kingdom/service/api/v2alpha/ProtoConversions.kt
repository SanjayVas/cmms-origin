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

package org.wfanet.measurement.kingdom.service.api.v2alpha

import com.google.protobuf.util.Timestamps
import com.google.type.interval
import org.wfanet.measurement.api.Version
import org.wfanet.measurement.api.v2alpha.DataProviderCertificateKey
import org.wfanet.measurement.api.v2alpha.DataProviderKey
import org.wfanet.measurement.api.v2alpha.DifferentialPrivacyParams
import org.wfanet.measurement.api.v2alpha.DuchyCertificateKey
import org.wfanet.measurement.api.v2alpha.EventGroup
import org.wfanet.measurement.api.v2alpha.Exchange
import org.wfanet.measurement.api.v2alpha.ExchangeKey
import org.wfanet.measurement.api.v2alpha.ExchangeStep
import org.wfanet.measurement.api.v2alpha.ExchangeStepAttempt
import org.wfanet.measurement.api.v2alpha.ExchangeStepAttemptKey
import org.wfanet.measurement.api.v2alpha.ExchangeStepAttemptKt
import org.wfanet.measurement.api.v2alpha.ExchangeStepKey
import org.wfanet.measurement.api.v2alpha.ExchangeWorkflow
import org.wfanet.measurement.api.v2alpha.Measurement
import org.wfanet.measurement.api.v2alpha.Measurement.DataProviderEntry
import org.wfanet.measurement.api.v2alpha.Measurement.Failure
import org.wfanet.measurement.api.v2alpha.Measurement.State
import org.wfanet.measurement.api.v2alpha.MeasurementConsumerCertificateKey
import org.wfanet.measurement.api.v2alpha.MeasurementKey
import org.wfanet.measurement.api.v2alpha.MeasurementKt.DataProviderEntryKt
import org.wfanet.measurement.api.v2alpha.MeasurementKt.dataProviderEntry
import org.wfanet.measurement.api.v2alpha.MeasurementKt.failure
import org.wfanet.measurement.api.v2alpha.MeasurementKt.resultPair
import org.wfanet.measurement.api.v2alpha.MeasurementSpec
import org.wfanet.measurement.api.v2alpha.ModelLine
import org.wfanet.measurement.api.v2alpha.ModelLine.Type
import org.wfanet.measurement.api.v2alpha.ModelLineKey
import org.wfanet.measurement.api.v2alpha.ModelOutage
import org.wfanet.measurement.api.v2alpha.ModelOutage.State as ModelOutageState
import org.wfanet.measurement.api.v2alpha.ModelOutageKey
import org.wfanet.measurement.api.v2alpha.ModelProviderKey
import org.wfanet.measurement.api.v2alpha.ModelRelease
import org.wfanet.measurement.api.v2alpha.ModelReleaseKey
import org.wfanet.measurement.api.v2alpha.ModelRollout
import org.wfanet.measurement.api.v2alpha.ModelRolloutKey
import org.wfanet.measurement.api.v2alpha.ModelShard
import org.wfanet.measurement.api.v2alpha.ModelShardKey
import org.wfanet.measurement.api.v2alpha.ModelShardKt.modelBlob
import org.wfanet.measurement.api.v2alpha.ModelSuite
import org.wfanet.measurement.api.v2alpha.ModelSuiteKey
import org.wfanet.measurement.api.v2alpha.ProtocolConfig
import org.wfanet.measurement.api.v2alpha.ProtocolConfig.NoiseMechanism
import org.wfanet.measurement.api.v2alpha.ProtocolConfigKt.direct
import org.wfanet.measurement.api.v2alpha.ProtocolConfigKt.liquidLegionsV2
import org.wfanet.measurement.api.v2alpha.ProtocolConfigKt.protocol
import org.wfanet.measurement.api.v2alpha.ProtocolConfigKt.reachOnlyLiquidLegionsV2
import org.wfanet.measurement.api.v2alpha.differentialPrivacyParams
import org.wfanet.measurement.api.v2alpha.exchange
import org.wfanet.measurement.api.v2alpha.exchangeStep
import org.wfanet.measurement.api.v2alpha.exchangeStepAttempt
import org.wfanet.measurement.api.v2alpha.liquidLegionsSketchParams
import org.wfanet.measurement.api.v2alpha.measurement
import org.wfanet.measurement.api.v2alpha.modelLine
import org.wfanet.measurement.api.v2alpha.modelOutage
import org.wfanet.measurement.api.v2alpha.modelRelease
import org.wfanet.measurement.api.v2alpha.modelRollout
import org.wfanet.measurement.api.v2alpha.modelShard
import org.wfanet.measurement.api.v2alpha.modelSuite
import org.wfanet.measurement.api.v2alpha.protocolConfig
import org.wfanet.measurement.api.v2alpha.reachOnlyLiquidLegionsSketchParams
import org.wfanet.measurement.api.v2alpha.signedData
import org.wfanet.measurement.common.identity.apiIdToExternalId
import org.wfanet.measurement.common.identity.externalIdToApiId
import org.wfanet.measurement.common.toLocalDate
import org.wfanet.measurement.internal.kingdom.DifferentialPrivacyParams as InternalDifferentialPrivacyParams
import org.wfanet.measurement.internal.kingdom.EventGroup as InternalEventGroup
import org.wfanet.measurement.internal.kingdom.Exchange as InternalExchange
import org.wfanet.measurement.internal.kingdom.ExchangeStep as InternalExchangeStep
import org.wfanet.measurement.internal.kingdom.ExchangeStepAttempt as InternalExchangeStepAttempt
import org.wfanet.measurement.internal.kingdom.ExchangeStepAttemptDetails
import org.wfanet.measurement.internal.kingdom.ExchangeStepAttemptDetailsKt
import org.wfanet.measurement.internal.kingdom.ExchangeWorkflow as InternalExchangeWorkflow
import org.wfanet.measurement.internal.kingdom.ExchangeWorkflowKt
import org.wfanet.measurement.internal.kingdom.Measurement as InternalMeasurement
import org.wfanet.measurement.internal.kingdom.Measurement.DataProviderValue
import org.wfanet.measurement.internal.kingdom.MeasurementKt.details
import org.wfanet.measurement.internal.kingdom.ModelLine as InternalModelLine
import org.wfanet.measurement.internal.kingdom.ModelOutage as InternalModelOutage
import org.wfanet.measurement.internal.kingdom.ModelRelease as InternalModelRelease
import org.wfanet.measurement.internal.kingdom.ModelRollout as InternalModelRollout
import org.wfanet.measurement.internal.kingdom.ModelShard as InternalModelShard
import org.wfanet.measurement.internal.kingdom.ModelSuite as InternalModelSuite
import org.wfanet.measurement.internal.kingdom.ProtocolConfig as InternalProtocolConfig
import org.wfanet.measurement.internal.kingdom.ProtocolConfig.NoiseMechanism as InternalNoiseMechanism
import org.wfanet.measurement.internal.kingdom.duchyProtocolConfig
import org.wfanet.measurement.internal.kingdom.exchangeWorkflow
import org.wfanet.measurement.internal.kingdom.measurement as internalMeasurement
import org.wfanet.measurement.internal.kingdom.modelLine as internalModelLine
import org.wfanet.measurement.internal.kingdom.modelOutage as internalModelOutage
import org.wfanet.measurement.internal.kingdom.modelRelease as internalModelRelease
import org.wfanet.measurement.internal.kingdom.modelRollout as internalModelRollout
import org.wfanet.measurement.internal.kingdom.modelShard as internalModelShard
import org.wfanet.measurement.internal.kingdom.modelSuite as internalModelSuite
import org.wfanet.measurement.internal.kingdom.protocolConfig as internalProtocolConfig
import org.wfanet.measurement.kingdom.deploy.common.Llv2ProtocolConfig
import org.wfanet.measurement.kingdom.deploy.common.RoLlv2ProtocolConfig

/** Converts an internal [InternalMeasurement.State] to a public [State]. */
fun InternalMeasurement.State.toState(): State =
  when (this) {
    InternalMeasurement.State.PENDING_REQUISITION_PARAMS,
    InternalMeasurement.State.PENDING_REQUISITION_FULFILLMENT ->
      State.AWAITING_REQUISITION_FULFILLMENT
    InternalMeasurement.State.PENDING_PARTICIPANT_CONFIRMATION,
    InternalMeasurement.State.PENDING_COMPUTATION -> State.COMPUTING
    InternalMeasurement.State.SUCCEEDED -> State.SUCCEEDED
    InternalMeasurement.State.FAILED -> State.FAILED
    InternalMeasurement.State.CANCELLED -> State.CANCELLED
    InternalMeasurement.State.STATE_UNSPECIFIED,
    InternalMeasurement.State.UNRECOGNIZED -> State.STATE_UNSPECIFIED
  }

/** Convert a public [State] to an internal [InternalMeasurement.State]. */
fun State.toInternalState(): List<InternalMeasurement.State> {
  return when (this) {
    State.AWAITING_REQUISITION_FULFILLMENT -> {
      listOf(
        InternalMeasurement.State.PENDING_REQUISITION_PARAMS,
        InternalMeasurement.State.PENDING_REQUISITION_FULFILLMENT
      )
    }
    State.COMPUTING -> {
      listOf(
        InternalMeasurement.State.PENDING_PARTICIPANT_CONFIRMATION,
        InternalMeasurement.State.PENDING_COMPUTATION
      )
    }
    State.SUCCEEDED -> listOf(InternalMeasurement.State.SUCCEEDED)
    State.FAILED -> listOf(InternalMeasurement.State.FAILED)
    State.CANCELLED -> listOf(InternalMeasurement.State.CANCELLED)
    State.STATE_UNSPECIFIED,
    State.UNRECOGNIZED -> listOf(InternalMeasurement.State.STATE_UNSPECIFIED)
  }
}

/** Converts an internal [InternalMeasurement.Failure.Reason] to a public [Failure.Reason]. */
fun InternalMeasurement.Failure.Reason.toReason(): Failure.Reason =
  when (this) {
    InternalMeasurement.Failure.Reason.CERTIFICATE_REVOKED -> Failure.Reason.CERTIFICATE_REVOKED
    InternalMeasurement.Failure.Reason.REQUISITION_REFUSED -> Failure.Reason.REQUISITION_REFUSED
    InternalMeasurement.Failure.Reason.COMPUTATION_PARTICIPANT_FAILED ->
      Failure.Reason.COMPUTATION_PARTICIPANT_FAILED
    InternalMeasurement.Failure.Reason.REASON_UNSPECIFIED,
    InternalMeasurement.Failure.Reason.UNRECOGNIZED -> Failure.Reason.REASON_UNSPECIFIED
  }

fun InternalDifferentialPrivacyParams.toDifferentialPrivacyParams(): DifferentialPrivacyParams {
  val source = this
  return differentialPrivacyParams {
    epsilon = source.epsilon
    delta = source.delta
  }
}

/** Converts an internal [InternalNoiseMechanism] to a public [NoiseMechanism]. */
fun InternalNoiseMechanism.toNoiseMechanism(): NoiseMechanism {
  return when (this) {
    InternalNoiseMechanism.GEOMETRIC -> NoiseMechanism.GEOMETRIC
    InternalNoiseMechanism.DISCRETE_GAUSSIAN -> NoiseMechanism.DISCRETE_GAUSSIAN
    InternalNoiseMechanism.NOISE_MECHANISM_UNSPECIFIED,
    InternalNoiseMechanism.UNRECOGNIZED -> error("invalid internal noise mechanism.")
  }
}

/** Converts an internal [InternalProtocolConfig] to a public [ProtocolConfig]. */
fun InternalProtocolConfig.toProtocolConfig(
  measurementTypeCase: MeasurementSpec.MeasurementTypeCase,
  dataProviderCount: Int,
): ProtocolConfig {
  require(dataProviderCount >= 1) { "Expected at least one DataProvider" }

  val source = this
  return protocolConfig {
    measurementType =
      when (measurementTypeCase) {
        MeasurementSpec.MeasurementTypeCase.MEASUREMENTTYPE_NOT_SET ->
          throw IllegalArgumentException("Measurement type not specified")
        MeasurementSpec.MeasurementTypeCase.REACH -> ProtocolConfig.MeasurementType.REACH
        MeasurementSpec.MeasurementTypeCase.REACH_AND_FREQUENCY ->
          ProtocolConfig.MeasurementType.REACH_AND_FREQUENCY
        MeasurementSpec.MeasurementTypeCase.IMPRESSION -> ProtocolConfig.MeasurementType.IMPRESSION
        MeasurementSpec.MeasurementTypeCase.DURATION -> ProtocolConfig.MeasurementType.DURATION
      }

    when (measurementType) {
      ProtocolConfig.MeasurementType.REACH,
      ProtocolConfig.MeasurementType.REACH_AND_FREQUENCY -> {
        if (dataProviderCount == 1) {
          protocols += protocol { direct = direct {} }
        } else {
          @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA") // Protobuf enum fields are never null.
          when (source.protocolCase) {
            InternalProtocolConfig.ProtocolCase.LIQUID_LEGIONS_V2 -> {
              protocols += protocol {
                liquidLegionsV2 = liquidLegionsV2 {
                  if (source.liquidLegionsV2.hasSketchParams()) {
                    val sourceSketchParams = source.liquidLegionsV2.sketchParams
                    sketchParams = liquidLegionsSketchParams {
                      decayRate = sourceSketchParams.decayRate
                      maxSize = sourceSketchParams.maxSize
                      samplingIndicatorSize = sourceSketchParams.samplingIndicatorSize
                    }
                  }
                  if (source.liquidLegionsV2.hasDataProviderNoise()) {
                    dataProviderNoise =
                      source.liquidLegionsV2.dataProviderNoise.toDifferentialPrivacyParams()
                  }
                  ellipticCurveId = source.liquidLegionsV2.ellipticCurveId
                  maximumFrequency = source.liquidLegionsV2.maximumFrequency
                  // Use `GEOMETRIC` for unspecified InternalNoiseMechanism for old Measurements.
                  noiseMechanism =
                    if (
                      source.liquidLegionsV2.noiseMechanism ==
                        InternalNoiseMechanism.NOISE_MECHANISM_UNSPECIFIED
                    ) {
                      NoiseMechanism.GEOMETRIC
                    } else {
                      source.liquidLegionsV2.noiseMechanism.toNoiseMechanism()
                    }
                }
              }
            }
            InternalProtocolConfig.ProtocolCase.REACH_ONLY_LIQUID_LEGIONS_V2 -> {
              protocols += protocol {
                reachOnlyLiquidLegionsV2 = reachOnlyLiquidLegionsV2 {
                  if (source.reachOnlyLiquidLegionsV2.hasSketchParams()) {
                    val sourceSketchParams = source.reachOnlyLiquidLegionsV2.sketchParams
                    sketchParams = reachOnlyLiquidLegionsSketchParams {
                      decayRate = sourceSketchParams.decayRate
                      maxSize = sourceSketchParams.maxSize
                    }
                  }
                  if (source.reachOnlyLiquidLegionsV2.hasDataProviderNoise()) {
                    dataProviderNoise =
                      source.reachOnlyLiquidLegionsV2.dataProviderNoise
                        .toDifferentialPrivacyParams()
                  }
                  ellipticCurveId = source.reachOnlyLiquidLegionsV2.ellipticCurveId
                  // Use `GEOMETRIC` for unspecified InternalNoiseMechanism for old Measurements.
                  noiseMechanism =
                    if (
                      source.reachOnlyLiquidLegionsV2.noiseMechanism ==
                        InternalNoiseMechanism.NOISE_MECHANISM_UNSPECIFIED
                    ) {
                      NoiseMechanism.GEOMETRIC
                    } else {
                      source.reachOnlyLiquidLegionsV2.noiseMechanism.toNoiseMechanism()
                    }
                }
              }
            }
            InternalProtocolConfig.ProtocolCase.PROTOCOL_NOT_SET -> error("Protocol not specified")
          }
        }
      }
      ProtocolConfig.MeasurementType.IMPRESSION,
      ProtocolConfig.MeasurementType.DURATION -> {
        protocols += protocol { direct = direct {} }
      }
      ProtocolConfig.MeasurementType.MEASUREMENT_TYPE_UNSPECIFIED,
      ProtocolConfig.MeasurementType.UNRECOGNIZED -> error("Invalid MeasurementType")
    }
  }
}

/** Converts an internal [InternalModelSuite] to a public [ModelSuite]. */
fun InternalModelSuite.toModelSuite(): ModelSuite {
  val source = this

  return modelSuite {
    name =
      ModelSuiteKey(
          externalIdToApiId(source.externalModelProviderId),
          externalIdToApiId(source.externalModelSuiteId)
        )
        .toName()
    displayName = source.displayName
    description = source.description
    createTime = source.createTime
  }
}

/** Converts a public [ModelSuite] to an internal [InternalModelSuite] */
fun ModelSuite.toInternal(modelProviderKey: ModelProviderKey): InternalModelSuite {
  val publicModelSuite = this

  return internalModelSuite {
    externalModelProviderId = apiIdToExternalId(modelProviderKey.modelProviderId)
    displayName = publicModelSuite.displayName
    description = publicModelSuite.description
  }
}

/** Converts a public [ModelRelease] to an internal [InternalModelRelease] */
fun ModelRelease.toInternal(modelSuiteKey: ModelSuiteKey): InternalModelRelease {
  return internalModelRelease {
    externalModelProviderId = apiIdToExternalId(modelSuiteKey.modelProviderId)
    externalModelSuiteId = apiIdToExternalId(modelSuiteKey.modelSuiteId)
  }
}

/** Converts an internal [InternalModelRelease] to a public [ModelRelease]. */
fun InternalModelRelease.toModelRelease(): ModelRelease {
  val source = this

  return modelRelease {
    name =
      ModelReleaseKey(
          externalIdToApiId(source.externalModelProviderId),
          externalIdToApiId(source.externalModelSuiteId),
          externalIdToApiId(source.externalModelReleaseId)
        )
        .toName()
    createTime = source.createTime
  }
}

/** Converts an internal [InternalModelLine.Type] to a public [Type]. */
fun InternalModelLine.Type.toType(): Type =
  when (this) {
    InternalModelLine.Type.DEV -> Type.DEV
    InternalModelLine.Type.PROD -> Type.PROD
    InternalModelLine.Type.HOLDBACK -> Type.HOLDBACK
    InternalModelLine.Type.UNRECOGNIZED,
    InternalModelLine.Type.TYPE_UNSPECIFIED -> Type.TYPE_UNSPECIFIED
  }

/** Convert a public [Type] to an internal [InternalModelLine.Type]. */
fun Type.toInternalType(): InternalModelLine.Type {
  return when (this) {
    Type.DEV -> InternalModelLine.Type.DEV
    Type.PROD -> InternalModelLine.Type.PROD
    Type.HOLDBACK -> InternalModelLine.Type.HOLDBACK
    Type.UNRECOGNIZED,
    Type.TYPE_UNSPECIFIED -> InternalModelLine.Type.TYPE_UNSPECIFIED
  }
}

/** Converts an internal [InternalModelLine] to a public [ModelLine]. */
fun InternalModelLine.toModelLine(): ModelLine {
  val source = this

  return modelLine {
    name =
      ModelLineKey(
          externalIdToApiId(source.externalModelProviderId),
          externalIdToApiId(source.externalModelSuiteId),
          externalIdToApiId(source.externalModelLineId)
        )
        .toName()
    displayName = source.displayName
    description = source.description
    activeStartTime = source.activeStartTime
    if (source.hasActiveEndTime()) {
      activeEndTime = source.activeEndTime
    }
    type = source.type.toType()
    if (source.externalHoldbackModelLineId != 0L) {
      holdbackModelLine =
        ModelLineKey(
            externalIdToApiId(source.externalModelProviderId),
            externalIdToApiId(source.externalModelSuiteId),
            externalIdToApiId(source.externalHoldbackModelLineId)
          )
          .toName()
    }
    createTime = source.createTime
    updateTime = source.updateTime
  }
}

/** Converts a public [ModelLine] to an internal [InternalModelLine] */
fun ModelLine.toInternal(modelSuiteKey: ModelSuiteKey): InternalModelLine {
  val publicModelLine = this

  return internalModelLine {
    externalModelProviderId = apiIdToExternalId(modelSuiteKey.modelProviderId)
    externalModelSuiteId = apiIdToExternalId(modelSuiteKey.modelSuiteId)
    displayName = publicModelLine.displayName
    description = publicModelLine.description
    activeStartTime = publicModelLine.activeStartTime
    if (publicModelLine.hasActiveEndTime()) {
      activeEndTime = publicModelLine.activeEndTime
    }
    type = publicModelLine.type.toInternalType()
    if (publicModelLine.holdbackModelLine.isNotBlank()) {
      externalHoldbackModelLineId = apiIdToExternalId(publicModelLine.holdbackModelLine)
    }
  }
}

/** Converts an internal [InternalModelOutage.State] to a public [ModelOutageState]. */
fun InternalModelOutage.State.toModelOutageState(): ModelOutageState =
  when (this) {
    InternalModelOutage.State.ACTIVE -> ModelOutageState.ACTIVE
    InternalModelOutage.State.DELETED -> ModelOutageState.DELETED
    InternalModelOutage.State.UNRECOGNIZED,
    InternalModelOutage.State.STATE_UNSPECIFIED -> ModelOutageState.STATE_UNSPECIFIED
  }

/** Converts an internal [InternalModelOutage] to a public [ModelOutage]. */
fun InternalModelOutage.toModelOutage(): ModelOutage {
  val source = this

  return modelOutage {
    name =
      ModelOutageKey(
          externalIdToApiId(source.externalModelProviderId),
          externalIdToApiId(source.externalModelSuiteId),
          externalIdToApiId(source.externalModelLineId),
          externalIdToApiId(source.externalModelOutageId)
        )
        .toName()
    outageInterval = interval {
      startTime = source.modelOutageStartTime
      endTime = source.modelOutageEndTime
    }
    state = source.state.toModelOutageState()
    createTime = source.createTime
    deleteTime = source.deleteTime
  }
}

/** Converts a public [ModelOutage] to an internal [InternalModelOutage] */
fun ModelOutage.toInternal(modelLineKey: ModelLineKey): InternalModelOutage {
  val publicModelOutage = this

  return internalModelOutage {
    externalModelProviderId = apiIdToExternalId(modelLineKey.modelProviderId)
    externalModelSuiteId = apiIdToExternalId(modelLineKey.modelSuiteId)
    externalModelLineId = apiIdToExternalId(modelLineKey.modelLineId)
    modelOutageStartTime = publicModelOutage.outageInterval.startTime
    modelOutageEndTime = publicModelOutage.outageInterval.endTime
  }
}

/** Converts an internal [InternalModelRollout] to a public [ModelRollout]. */
fun InternalModelRollout.toModelRollout(): ModelRollout {
  val source = this

  return modelRollout {
    name =
      ModelRolloutKey(
          externalIdToApiId(source.externalModelProviderId),
          externalIdToApiId(source.externalModelSuiteId),
          externalIdToApiId(source.externalModelLineId),
          externalIdToApiId(source.externalModelRolloutId)
        )
        .toName()

    if (Timestamps.compare(source.rolloutPeriodStartTime, source.rolloutPeriodEndTime) == 0) {
      instantRolloutTime = source.rolloutPeriodStartTime
    } else {
      gradualRolloutPeriod = interval {
        startTime = source.rolloutPeriodStartTime
        endTime = source.rolloutPeriodEndTime
      }
    }

    rolloutFreezeTime = source.rolloutFreezeTime
    if (source.externalPreviousModelRolloutId != 0L) {
      previousModelRollout =
        ModelRolloutKey(
            externalIdToApiId(source.externalModelProviderId),
            externalIdToApiId(source.externalModelSuiteId),
            externalIdToApiId(source.externalModelLineId),
            externalIdToApiId(source.externalPreviousModelRolloutId)
          )
          .toName()
    }
    modelRelease =
      ModelReleaseKey(
          externalIdToApiId(source.externalModelProviderId),
          externalIdToApiId(source.externalModelSuiteId),
          externalIdToApiId(source.externalModelReleaseId)
        )
        .toName()
    createTime = source.createTime
    updateTime = source.updateTime
  }
}

/** Converts a public [ModelRollout] to an internal [InternalModelRollout] */
fun ModelRollout.toInternal(
  modelLineKey: ModelLineKey,
  modelReleaseKey: ModelReleaseKey
): InternalModelRollout {
  val publicModelRollout = this

  return internalModelRollout {
    externalModelProviderId = apiIdToExternalId(modelLineKey.modelProviderId)
    externalModelSuiteId = apiIdToExternalId(modelLineKey.modelSuiteId)
    externalModelLineId = apiIdToExternalId(modelLineKey.modelLineId)

    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    when (publicModelRollout.rolloutDeployPeriodCase) {
      ModelRollout.RolloutDeployPeriodCase.GRADUAL_ROLLOUT_PERIOD -> {
        rolloutPeriodStartTime = publicModelRollout.gradualRolloutPeriod.startTime
        rolloutPeriodEndTime = publicModelRollout.gradualRolloutPeriod.endTime
      }
      ModelRollout.RolloutDeployPeriodCase.INSTANT_ROLLOUT_TIME -> {
        rolloutPeriodStartTime = publicModelRollout.instantRolloutTime
        rolloutPeriodEndTime = publicModelRollout.instantRolloutTime
      }
      ModelRollout.RolloutDeployPeriodCase.ROLLOUTDEPLOYPERIOD_NOT_SET -> {
        error("RolloutDeployPeriod not set.")
      }
    }

    rolloutFreezeTime = publicModelRollout.rolloutFreezeTime
    externalModelReleaseId = apiIdToExternalId(modelReleaseKey.modelReleaseId)
  }
}

/** Converts an internal [InternalModelShard] to a public [ModelShard]. */
fun InternalModelShard.toModelShard(): ModelShard {
  val source = this

  return modelShard {
    name =
      ModelShardKey(
          externalIdToApiId(source.externalDataProviderId),
          externalIdToApiId(source.externalModelShardId)
        )
        .toName()
    modelRelease =
      ModelReleaseKey(
          externalIdToApiId(source.externalModelProviderId),
          externalIdToApiId(source.externalModelSuiteId),
          externalIdToApiId(source.externalModelReleaseId)
        )
        .toName()
    modelBlob = modelBlob { modelBlobPath = source.modelBlobPath }
    createTime = source.createTime
  }
}

/** Converts a public [ModelShard] to an internal [InternalModelShard] */
fun ModelShard.toInternal(
  dataProviderKey: DataProviderKey,
  modelReleaseKey: ModelReleaseKey
): InternalModelShard {
  val publicModelShard = this

  return internalModelShard {
    externalDataProviderId = apiIdToExternalId(dataProviderKey.dataProviderId)
    externalModelProviderId = apiIdToExternalId(modelReleaseKey.modelProviderId)
    externalModelSuiteId = apiIdToExternalId(modelReleaseKey.modelSuiteId)
    externalModelReleaseId = apiIdToExternalId(modelReleaseKey.modelReleaseId)
    modelBlobPath = publicModelShard.modelBlob.modelBlobPath
  }
}

/** Converts an internal [InternalMeasurement] to a public [Measurement]. */
fun InternalMeasurement.toMeasurement(): Measurement {
  val source = this
  check(Version.fromString(source.details.apiVersion) == Version.V2_ALPHA) {
    "Incompatible API version ${source.details.apiVersion}"
  }
  return measurement {
    name =
      MeasurementKey(
          externalIdToApiId(source.externalMeasurementConsumerId),
          externalIdToApiId(source.externalMeasurementId)
        )
        .toName()
    measurementConsumerCertificate =
      MeasurementConsumerCertificateKey(
          externalIdToApiId(source.externalMeasurementConsumerId),
          externalIdToApiId(source.externalMeasurementConsumerCertificateId)
        )
        .toName()
    measurementSpec = signedData {
      data = source.details.measurementSpec
      signature = source.details.measurementSpecSignature
    }
    dataProviders +=
      source.dataProvidersMap.entries.map(Map.Entry<Long, DataProviderValue>::toDataProviderEntry)

    val measurementTypeCase =
      MeasurementSpec.parseFrom(this.measurementSpec.data).measurementTypeCase

    protocolConfig =
      source.details.protocolConfig.toProtocolConfig(
        measurementTypeCase,
        dataProvidersCount,
      )

    state = source.state.toState()
    results +=
      source.resultsList.map {
        val certificateApiId = externalIdToApiId(it.externalCertificateId)
        resultPair {
          if (it.externalAggregatorDuchyId.isNotBlank()) {
            certificate =
              DuchyCertificateKey(it.externalAggregatorDuchyId, certificateApiId).toName()
          } else if (it.externalDataProviderId != 0L) {
            certificate =
              DataProviderCertificateKey(
                  externalIdToApiId(it.externalDataProviderId),
                  certificateApiId
                )
                .toName()
          }
          encryptedResult = it.encryptedResult
        }
      }
    measurementReferenceId = source.providedMeasurementId
    if (source.details.hasFailure()) {
      failure = failure {
        reason = source.details.failure.reason.toReason()
        message = source.details.failure.message
      }
    }
  }
}

/** Converts an internal [DataProviderValue] to a public [DataProviderEntry.Value]. */
fun DataProviderValue.toDataProviderEntryValue(dataProviderId: String): DataProviderEntry.Value {
  val dataProviderValue = this
  return DataProviderEntryKt.value {
    dataProviderCertificate =
      DataProviderCertificateKey(
          dataProviderId,
          externalIdToApiId(externalDataProviderCertificateId)
        )
        .toName()
    dataProviderPublicKey = signedData {
      data = dataProviderValue.dataProviderPublicKey
      signature = dataProviderPublicKeySignature
    }
    encryptedRequisitionSpec = dataProviderValue.encryptedRequisitionSpec
    nonceHash = dataProviderValue.nonceHash
  }
}

/** Converts an internal data provider map entry to a public [DataProviderEntry]. */
fun Map.Entry<Long, DataProviderValue>.toDataProviderEntry(): DataProviderEntry {
  val mapEntry = this
  return dataProviderEntry {
    key = DataProviderKey(externalIdToApiId(mapEntry.key)).toName()
    value = mapEntry.value.toDataProviderEntryValue(externalIdToApiId(mapEntry.key))
  }
}

/**
 * Converts a public [Measurement] to an internal [InternalMeasurement] for creation.
 *
 * @throws [IllegalStateException] if MeasurementType not specified
 */
fun Measurement.toInternal(
  measurementConsumerCertificateKey: MeasurementConsumerCertificateKey,
  dataProvidersMap: Map<Long, DataProviderValue>,
  measurementSpecProto: MeasurementSpec
): InternalMeasurement {
  val publicMeasurement = this

  return internalMeasurement {
    providedMeasurementId = measurementReferenceId
    externalMeasurementConsumerId =
      apiIdToExternalId(measurementConsumerCertificateKey.measurementConsumerId)
    externalMeasurementConsumerCertificateId =
      apiIdToExternalId(measurementConsumerCertificateKey.certificateId)
    dataProviders.putAll(dataProvidersMap)
    details = details {
      apiVersion = Version.V2_ALPHA.string
      measurementSpec = publicMeasurement.measurementSpec.data
      measurementSpecSignature = publicMeasurement.measurementSpec.signature

      @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA") // Proto enum fields are never null.
      when (measurementSpecProto.measurementTypeCase) {
        MeasurementSpec.MeasurementTypeCase.REACH -> {
          if (dataProvidersCount > 1) {
            if (RoLlv2ProtocolConfig.enabled) {
              protocolConfig = internalProtocolConfig {
                externalProtocolConfigId = RoLlv2ProtocolConfig.name
                reachOnlyLiquidLegionsV2 = RoLlv2ProtocolConfig.protocolConfig
              }
              duchyProtocolConfig = duchyProtocolConfig {
                reachOnlyLiquidLegionsV2 = RoLlv2ProtocolConfig.duchyProtocolConfig
              }
            } else {
              protocolConfig = internalProtocolConfig {
                externalProtocolConfigId = Llv2ProtocolConfig.name
                liquidLegionsV2 = Llv2ProtocolConfig.protocolConfig
              }
              duchyProtocolConfig = duchyProtocolConfig {
                liquidLegionsV2 = Llv2ProtocolConfig.duchyProtocolConfig
              }
            }
          }
        }
        MeasurementSpec.MeasurementTypeCase.REACH_AND_FREQUENCY -> {
          if (dataProvidersCount > 1) {
            protocolConfig = internalProtocolConfig {
              externalProtocolConfigId = Llv2ProtocolConfig.name
              liquidLegionsV2 = Llv2ProtocolConfig.protocolConfig
            }
            duchyProtocolConfig = duchyProtocolConfig {
              liquidLegionsV2 = Llv2ProtocolConfig.duchyProtocolConfig
            }
          }
        }
        MeasurementSpec.MeasurementTypeCase.IMPRESSION,
        MeasurementSpec.MeasurementTypeCase.DURATION, -> {}
        MeasurementSpec.MeasurementTypeCase.MEASUREMENTTYPE_NOT_SET ->
          error("MeasurementType not set.")
      }
    }
  }
}

/** @throws [IllegalStateException] if InternalExchange.State not specified */
fun InternalExchange.toExchange(): Exchange {
  val source = this
  val exchangeKey =
    ExchangeKey(
      recurringExchangeId = externalIdToApiId(externalRecurringExchangeId),
      exchangeId = date.toLocalDate().toString()
    )
  @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA") // ProtoBuf enum fields cannot be null.
  val state =
    when (source.state) {
      InternalExchange.State.ACTIVE -> Exchange.State.ACTIVE
      InternalExchange.State.SUCCEEDED -> Exchange.State.SUCCEEDED
      InternalExchange.State.FAILED -> Exchange.State.FAILED
      InternalExchange.State.STATE_UNSPECIFIED,
      InternalExchange.State.UNRECOGNIZED -> error("Invalid InternalExchange state.")
    }

  return exchange {
    name = exchangeKey.toName()
    date = source.date
    this.state = state
    auditTrailHash = source.details.auditTrailHash
    // TODO(@yunyeng): Add graphvizRepresentation to Exchange proto.
    graphvizRepresentation = ""
  }
}

/** @throws [IllegalStateException] if InternalExchangeStep.State not specified */
fun InternalExchangeStep.toV2Alpha(): ExchangeStep {
  val exchangeStepKey =
    ExchangeStepKey(
      recurringExchangeId = externalIdToApiId(externalRecurringExchangeId),
      exchangeId = date.toLocalDate().toString(),
      exchangeStepId = stepIndex.toString()
    )
  return exchangeStep {
    name = exchangeStepKey.toName()
    state = v2AlphaState
    stepIndex = this@toV2Alpha.stepIndex
    exchangeDate = date
    serializedExchangeWorkflow = this@toV2Alpha.serializedExchangeWorkflow
  }
}

/** @throws [IllegalStateException] if State not specified */
fun ExchangeStepAttempt.State.toInternal(): InternalExchangeStepAttempt.State {
  return when (this) {
    ExchangeStepAttempt.State.STATE_UNSPECIFIED,
    ExchangeStepAttempt.State.UNRECOGNIZED -> error("Invalid ExchangeStepAttempt state: $this")
    ExchangeStepAttempt.State.ACTIVE -> InternalExchangeStepAttempt.State.ACTIVE
    ExchangeStepAttempt.State.SUCCEEDED -> InternalExchangeStepAttempt.State.SUCCEEDED
    ExchangeStepAttempt.State.FAILED -> InternalExchangeStepAttempt.State.FAILED
    ExchangeStepAttempt.State.FAILED_STEP -> InternalExchangeStepAttempt.State.FAILED_STEP
  }
}

fun Iterable<ExchangeStepAttempt.DebugLogEntry>.toInternal():
  Iterable<ExchangeStepAttemptDetails.DebugLog> {
  return map { apiProto ->
    ExchangeStepAttemptDetailsKt.debugLog {
      time = apiProto.entryTime
      message = apiProto.message
    }
  }
}

/** @throws [IllegalStateException] if InternalExchangeStepAttempt.State not specified */
fun InternalExchangeStepAttempt.toV2Alpha(): ExchangeStepAttempt {
  val key =
    ExchangeStepAttemptKey(
      recurringExchangeId = externalIdToApiId(externalRecurringExchangeId),
      exchangeId = date.toLocalDate().toString(),
      exchangeStepId = stepIndex.toString(),
      exchangeStepAttemptId = this@toV2Alpha.attemptNumber.toString()
    )
  return exchangeStepAttempt {
    name = key.toName()
    attemptNumber = this@toV2Alpha.attemptNumber
    state = this@toV2Alpha.state.toV2Alpha()
    debugLogEntries += details.debugLogEntriesList.map { it.toV2Alpha() }
    startTime = details.startTime
    updateTime = details.updateTime
  }
}

/** @throws [IllegalStateException] if State not specified */
fun ExchangeStep.State.toInternal(): InternalExchangeStep.State {
  return when (this) {
    ExchangeStep.State.BLOCKED -> InternalExchangeStep.State.BLOCKED
    ExchangeStep.State.READY -> InternalExchangeStep.State.READY
    ExchangeStep.State.READY_FOR_RETRY -> InternalExchangeStep.State.READY_FOR_RETRY
    ExchangeStep.State.IN_PROGRESS -> InternalExchangeStep.State.IN_PROGRESS
    ExchangeStep.State.SUCCEEDED -> InternalExchangeStep.State.SUCCEEDED
    ExchangeStep.State.FAILED -> InternalExchangeStep.State.FAILED
    ExchangeStep.State.STATE_UNSPECIFIED,
    ExchangeStep.State.UNRECOGNIZED -> error("Invalid ExchangeStep state: $this")
  }
}

private fun ExchangeStepAttemptDetails.DebugLog.toV2Alpha(): ExchangeStepAttempt.DebugLogEntry {
  return ExchangeStepAttemptKt.debugLogEntry {
    entryTime = this@toV2Alpha.time
    message = this@toV2Alpha.message
  }
}

private fun InternalExchangeStepAttempt.State.toV2Alpha(): ExchangeStepAttempt.State {
  return when (this) {
    InternalExchangeStepAttempt.State.STATE_UNSPECIFIED,
    InternalExchangeStepAttempt.State.UNRECOGNIZED ->
      error("Invalid InternalExchangeStepAttempt state: $this")
    InternalExchangeStepAttempt.State.ACTIVE -> ExchangeStepAttempt.State.ACTIVE
    InternalExchangeStepAttempt.State.SUCCEEDED -> ExchangeStepAttempt.State.SUCCEEDED
    InternalExchangeStepAttempt.State.FAILED -> ExchangeStepAttempt.State.FAILED
    InternalExchangeStepAttempt.State.FAILED_STEP -> ExchangeStepAttempt.State.FAILED_STEP
  }
}

private val InternalExchangeStep.v2AlphaState: ExchangeStep.State
  get() {
    @Suppress("WHEN_ENUM_CAN_BE_NULL_IN_JAVA")
    return when (this.state) {
      InternalExchangeStep.State.BLOCKED -> ExchangeStep.State.BLOCKED
      InternalExchangeStep.State.READY -> ExchangeStep.State.READY
      InternalExchangeStep.State.READY_FOR_RETRY -> ExchangeStep.State.READY_FOR_RETRY
      InternalExchangeStep.State.IN_PROGRESS -> ExchangeStep.State.IN_PROGRESS
      InternalExchangeStep.State.SUCCEEDED -> ExchangeStep.State.SUCCEEDED
      InternalExchangeStep.State.FAILED -> ExchangeStep.State.FAILED
      InternalExchangeStep.State.STATE_UNSPECIFIED,
      InternalExchangeStep.State.UNRECOGNIZED -> error("Invalid InternalExchangeStep state: $this")
    }
  }

/** @throws [IllegalArgumentException] if step dependencies invalid */
fun ExchangeWorkflow.toInternal(): InternalExchangeWorkflow {
  val labelsMap = mutableMapOf<String, MutableSet<Pair<String, Int>>>()
  for ((index, step) in stepsList.withIndex()) {
    for (outputLabel in step.outputLabelsMap.values) {
      labelsMap.getOrPut(outputLabel) { mutableSetOf() }.add(step.stepId to index)
    }
  }
  val internalSteps =
    stepsList.mapIndexed { index, step ->
      ExchangeWorkflowKt.step {
        stepIndex = index
        party = step.party.toInternal()
        prerequisiteStepIndices +=
          step.inputLabelsMap.values
            .flatMap { value ->
              val prerequisites = labelsMap.getOrDefault(value, emptyList())
              prerequisites.forEach { (stepId, stepIndex) ->
                require(step.hasCopyFromPreviousExchangeStep() || stepIndex < index) {
                  "Step ${step.stepId} with index $index cannot depend on step $stepId with" +
                    " index $stepIndex. To depend on another step, the index must be greater than" +
                    " the prerequisite step"
                }
              }
              prerequisites.map { it.second }
            }
            .filter { it < index }
            .toSet()
      }
    }

  return exchangeWorkflow { steps += internalSteps }
}

/** @throws [IllegalArgumentException] if Provider not specified */
fun ExchangeWorkflow.Party.toInternal(): InternalExchangeWorkflow.Party {
  return when (this) {
    ExchangeWorkflow.Party.DATA_PROVIDER -> InternalExchangeWorkflow.Party.DATA_PROVIDER
    ExchangeWorkflow.Party.MODEL_PROVIDER -> InternalExchangeWorkflow.Party.MODEL_PROVIDER
    ExchangeWorkflow.Party.PARTY_UNSPECIFIED,
    ExchangeWorkflow.Party.UNRECOGNIZED ->
      throw IllegalArgumentException("Provider is not set for the Exchange Step.")
  }
}

fun InternalEventGroup.State.toV2Alpha(): EventGroup.State {
  return when (this) {
    InternalEventGroup.State.STATE_UNSPECIFIED -> EventGroup.State.STATE_UNSPECIFIED
    InternalEventGroup.State.ACTIVE -> EventGroup.State.ACTIVE
    InternalEventGroup.State.DELETED -> EventGroup.State.DELETED
    InternalEventGroup.State.UNRECOGNIZED -> error("Invalid InternalEventGroup state: $this")
  }
}
