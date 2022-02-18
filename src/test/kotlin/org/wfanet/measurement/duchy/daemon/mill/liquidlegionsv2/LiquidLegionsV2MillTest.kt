// Copyright 2020 The Cross-Media Measurement Authors
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

package org.wfanet.measurement.duchy.daemon.mill.liquidlegionsv2.crypto

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.extensions.proto.ProtoTruth.assertThat
import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteString
import io.grpc.Status
import java.time.Clock
import java.time.Duration
import java.util.Base64
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.mockito.kotlin.UseConstructor
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.wfanet.anysketch.crypto.CombineElGamalPublicKeysRequest
import org.wfanet.anysketch.crypto.CombineElGamalPublicKeysResponse
import org.wfanet.measurement.api.v2alpha.ElGamalPublicKey as V2AlphaElGamalPublicKey
import org.wfanet.measurement.api.v2alpha.measurementSpec
import org.wfanet.measurement.common.crypto.SigningKeyHandle
import org.wfanet.measurement.common.crypto.readCertificate
import org.wfanet.measurement.common.crypto.readPrivateKey
import org.wfanet.measurement.common.crypto.testing.FIXED_SERVER_CERT_DER_FILE
import org.wfanet.measurement.common.crypto.testing.FIXED_SERVER_KEY_DER_FILE
import org.wfanet.measurement.common.crypto.tink.TinkPrivateKeyHandle
import org.wfanet.measurement.common.flatten
import org.wfanet.measurement.common.grpc.testing.GrpcTestServerRule
import org.wfanet.measurement.common.grpc.testing.mockService
import org.wfanet.measurement.common.testing.chainRulesSequentially
import org.wfanet.measurement.common.testing.verifyProtoArgument
import org.wfanet.measurement.common.throttler.MinimumIntervalThrottler
import org.wfanet.measurement.consent.client.common.toEncryptionPublicKey
import org.wfanet.measurement.duchy.daemon.mill.Certificate
import org.wfanet.measurement.duchy.daemon.mill.liquidlegionsv2.LiquidLegionsV2Mill
import org.wfanet.measurement.duchy.daemon.testing.TestRequisition
import org.wfanet.measurement.duchy.daemon.utils.toDuchyEncryptionPublicKey
import org.wfanet.measurement.duchy.db.computation.ComputationDataClients
import org.wfanet.measurement.duchy.db.computation.testing.FakeComputationsDatabase
import org.wfanet.measurement.duchy.service.internal.computations.ComputationsService
import org.wfanet.measurement.duchy.service.internal.computations.newEmptyOutputBlobMetadata
import org.wfanet.measurement.duchy.service.internal.computations.newInputBlobMetadata
import org.wfanet.measurement.duchy.service.internal.computations.newOutputBlobMetadata
import org.wfanet.measurement.duchy.storage.ComputationBlobContext
import org.wfanet.measurement.duchy.storage.ComputationStore
import org.wfanet.measurement.duchy.storage.RequisitionBlobContext
import org.wfanet.measurement.duchy.storage.RequisitionStore
import org.wfanet.measurement.duchy.toProtocolStage
import org.wfanet.measurement.internal.duchy.ComputationBlobDependency
import org.wfanet.measurement.internal.duchy.ComputationDetails
import org.wfanet.measurement.internal.duchy.ComputationDetails.CompletedReason
import org.wfanet.measurement.internal.duchy.ComputationStatsGrpcKt.ComputationStatsCoroutineImplBase
import org.wfanet.measurement.internal.duchy.ComputationStatsGrpcKt.ComputationStatsCoroutineStub
import org.wfanet.measurement.internal.duchy.ComputationToken
import org.wfanet.measurement.internal.duchy.ComputationsGrpcKt.ComputationsCoroutineStub
import org.wfanet.measurement.internal.duchy.ElGamalKeyPair
import org.wfanet.measurement.internal.duchy.ElGamalPublicKey
import org.wfanet.measurement.internal.duchy.config.LiquidLegionsV2SetupConfig.RoleInComputation
import org.wfanet.measurement.internal.duchy.copy
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseOneAtAggregatorRequest
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseOneAtAggregatorResponse
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseOneRequest
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseOneResponse
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseThreeAtAggregatorRequest
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseThreeAtAggregatorResponse
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseThreeRequest
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseThreeResponse
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseTwoAtAggregatorRequest
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseTwoAtAggregatorResponse
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseTwoRequest
import org.wfanet.measurement.internal.duchy.protocol.CompleteExecutionPhaseTwoResponse
import org.wfanet.measurement.internal.duchy.protocol.CompleteInitializationPhaseRequest
import org.wfanet.measurement.internal.duchy.protocol.CompleteInitializationPhaseResponse
import org.wfanet.measurement.internal.duchy.protocol.CompleteSetupPhaseRequest
import org.wfanet.measurement.internal.duchy.protocol.CompleteSetupPhaseResponse
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.ComputationDetails.ComputationParticipant
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.ComputationDetails.Parameters
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.COMPLETE
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.CONFIRMATION_PHASE
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.EXECUTION_PHASE_ONE
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.EXECUTION_PHASE_THREE
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.EXECUTION_PHASE_TWO
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.INITIALIZATION_PHASE
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.SETUP_PHASE
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.WAIT_EXECUTION_PHASE_ONE_INPUTS
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.WAIT_EXECUTION_PHASE_THREE_INPUTS
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.WAIT_EXECUTION_PHASE_TWO_INPUTS
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.WAIT_REQUISITIONS_AND_KEY_SET
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.WAIT_SETUP_PHASE_INPUTS
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsSketchAggregationV2.Stage.WAIT_TO_START
import org.wfanet.measurement.internal.duchy.protocol.LiquidLegionsV2NoiseConfig
import org.wfanet.measurement.storage.Store.Blob
import org.wfanet.measurement.storage.filesystem.FileSystemStorageClient
import org.wfanet.measurement.storage.read
import org.wfanet.measurement.system.v1alpha.AdvanceComputationRequest
import org.wfanet.measurement.system.v1alpha.AdvanceComputationResponse
import org.wfanet.measurement.system.v1alpha.Computation
import org.wfanet.measurement.system.v1alpha.ComputationControlGrpcKt.ComputationControlCoroutineImplBase
import org.wfanet.measurement.system.v1alpha.ComputationControlGrpcKt.ComputationControlCoroutineStub
import org.wfanet.measurement.system.v1alpha.ComputationKey
import org.wfanet.measurement.system.v1alpha.ComputationLogEntriesGrpcKt.ComputationLogEntriesCoroutineImplBase as SystemComputationLogEntriesCoroutineImplBase
import org.wfanet.measurement.system.v1alpha.ComputationLogEntriesGrpcKt.ComputationLogEntriesCoroutineStub as SystemComputationLogEntriesCoroutineStub
import org.wfanet.measurement.system.v1alpha.ComputationLogEntry
import org.wfanet.measurement.system.v1alpha.ComputationParticipantKey
import org.wfanet.measurement.system.v1alpha.ComputationParticipantsGrpcKt.ComputationParticipantsCoroutineImplBase as SystemComputationParticipantsCoroutineImplBase
import org.wfanet.measurement.system.v1alpha.ComputationParticipantsGrpcKt.ComputationParticipantsCoroutineStub as SystemComputationParticipantsCoroutineStub
import org.wfanet.measurement.system.v1alpha.ComputationsGrpcKt.ComputationsCoroutineImplBase as SystemComputationsCoroutineImplBase
import org.wfanet.measurement.system.v1alpha.ComputationsGrpcKt.ComputationsCoroutineStub as SystemComputationsCoroutineStub
import org.wfanet.measurement.system.v1alpha.ConfirmComputationParticipantRequest
import org.wfanet.measurement.system.v1alpha.FailComputationParticipantRequest
import org.wfanet.measurement.system.v1alpha.LiquidLegionsV2
import org.wfanet.measurement.system.v1alpha.LiquidLegionsV2.Description.EXECUTION_PHASE_ONE_INPUT
import org.wfanet.measurement.system.v1alpha.LiquidLegionsV2.Description.EXECUTION_PHASE_THREE_INPUT
import org.wfanet.measurement.system.v1alpha.LiquidLegionsV2.Description.EXECUTION_PHASE_TWO_INPUT
import org.wfanet.measurement.system.v1alpha.LiquidLegionsV2.Description.SETUP_PHASE_INPUT
import org.wfanet.measurement.system.v1alpha.Requisition
import org.wfanet.measurement.system.v1alpha.SetComputationResultRequest
import org.wfanet.measurement.system.v1alpha.SetParticipantRequisitionParamsRequest
import org.wfanet.measurement.system.v1alpha.setComputationResultRequest

private const val PUBLIC_API_VERSION = "v2alpha"

private const val WORKER_COUNT = 3
private const val MILL_ID = "a nice mill"
private const val DUCHY_ONE_NAME = "DUCHY_ONE"
private const val DUCHY_TWO_NAME = "DUCHY_TWO"
private const val DUCHY_THREE_NAME = "DUCHY_THREE"
private const val MAX_FREQUENCY = 15
private const val DECAY_RATE = 12.0
private const val SKETCH_SIZE = 100_000L
private const val CURVE_ID = 415L // NID_X9_62_prime256v1

private const val LOCAL_ID = 1234L
private const val GLOBAL_ID = LOCAL_ID.toString()

private val DUCHY_ONE_KEY_PAIR =
  ElGamalKeyPair.newBuilder()
    .apply {
      publicKeyBuilder.apply {
        generator = ByteString.copyFromUtf8("generator_1")
        element = ByteString.copyFromUtf8("element_1")
      }
      secretKey = ByteString.copyFromUtf8("secret_key_1")
    }
    .build()
private val DUCHY_TWO_PUBLIC_KEY =
  ElGamalPublicKey.newBuilder()
    .apply {
      generator = ByteString.copyFromUtf8("generator_2")
      element = ByteString.copyFromUtf8("element_2")
    }
    .build()
private val DUCHY_THREE_PUBLIC_KEY =
  ElGamalPublicKey.newBuilder()
    .apply {
      generator = ByteString.copyFromUtf8("generator_3")
      element = ByteString.copyFromUtf8("element_3")
    }
    .build()
private val COMBINED_PUBLIC_KEY =
  ElGamalPublicKey.newBuilder()
    .apply {
      generator = ByteString.copyFromUtf8("generator_1_generator_2_generator_3")
      element = ByteString.copyFromUtf8("element_1_element_2_element_3")
    }
    .build()
private val PARTIALLY_COMBINED_PUBLIC_KEY =
  ElGamalPublicKey.newBuilder()
    .apply {
      generator = ByteString.copyFromUtf8("generator_2_generator_3")
      element = ByteString.copyFromUtf8("element_2_element_3")
    }
    .build()

private val TEST_NOISE_CONFIG =
  LiquidLegionsV2NoiseConfig.newBuilder()
    .apply {
      reachNoiseConfigBuilder.apply {
        blindHistogramNoiseBuilder.apply {
          epsilon = 1.0
          delta = 2.0
        }
        noiseForPublisherNoiseBuilder.apply {
          epsilon = 3.0
          delta = 4.0
        }
        globalReachDpNoiseBuilder.apply {
          epsilon = 5.0
          delta = 6.0
        }
      }
      frequencyNoiseConfigBuilder.apply {
        epsilon = 7.0
        delta = 8.0
      }
    }
    .build()

private val LLV2_PARAMETERS =
  Parameters.newBuilder()
    .apply {
      maximumFrequency = MAX_FREQUENCY
      liquidLegionsSketchBuilder.apply {
        decayRate = DECAY_RATE
        size = SKETCH_SIZE
      }
      noise = TEST_NOISE_CONFIG
      ellipticCurveId = CURVE_ID.toInt()
    }
    .build()

// In the test, use the same set of cert and encryption key for all parties.
private const val CONSENT_SIGNALING_CERT_NAME = "Just a name"
private val CONSENT_SIGNALING_CERT_DER = FIXED_SERVER_CERT_DER_FILE.readBytes().toByteString()
private val CONSENT_SIGNALING_PRIVATE_KEY_DER = FIXED_SERVER_KEY_DER_FILE.readBytes().toByteString()
private val ENCRYPTION_PRIVATE_KEY = TinkPrivateKeyHandle.generateEcies()
private val ENCRYPTION_PUBLIC_KEY: org.wfanet.measurement.api.v2alpha.EncryptionPublicKey =
  ENCRYPTION_PRIVATE_KEY.publicKey.toEncryptionPublicKey()

/** A public Key used for consent signaling check. */
private val CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY =
  V2AlphaElGamalPublicKey.newBuilder()
    .apply {
      generator = ByteString.copyFromUtf8("generator-foo")
      element = ByteString.copyFromUtf8("element-foo")
    }
    .build()
/** A pre-computed signature of the CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY. */
private val CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY_SINGATURE =
  ByteString.copyFrom(
    Base64.getDecoder()
      .decode(
        "MEUCIB2HWi/udHE9YlCH6n9lnGY12I9F1ra1SRWoJrIOXGiAAiEAm90wrJRqABFC5sjej+" +
          "zjSBOMHcmFOEpfW9tXaCla6Qs="
      )
  )

private val COMPUTATION_PARTICIPANT_1 =
  ComputationParticipant.newBuilder()
    .apply {
      duchyId = DUCHY_ONE_NAME
      publicKey = DUCHY_ONE_KEY_PAIR.publicKey
      elGamalPublicKey = CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY.toByteString()
      elGamalPublicKeySignature = CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY_SINGATURE
      duchyCertificateDer = CONSENT_SIGNALING_CERT_DER
    }
    .build()
private val COMPUTATION_PARTICIPANT_2 =
  ComputationParticipant.newBuilder()
    .apply {
      duchyId = DUCHY_TWO_NAME
      publicKey = DUCHY_TWO_PUBLIC_KEY
      elGamalPublicKey = CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY.toByteString()
      elGamalPublicKeySignature = CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY_SINGATURE
      duchyCertificateDer = CONSENT_SIGNALING_CERT_DER
    }
    .build()
private val COMPUTATION_PARTICIPANT_3 =
  ComputationParticipant.newBuilder()
    .apply {
      duchyId = DUCHY_THREE_NAME
      publicKey = DUCHY_THREE_PUBLIC_KEY
      elGamalPublicKey = CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY.toByteString()
      elGamalPublicKeySignature = CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY_SINGATURE
      duchyCertificateDer = CONSENT_SIGNALING_CERT_DER
    }
    .build()

private val TEST_REQUISITION_1 = TestRequisition("111") { SERIALIZED_MEASUREMENT_SPEC }
private val TEST_REQUISITION_2 = TestRequisition("222") { SERIALIZED_MEASUREMENT_SPEC }
private val TEST_REQUISITION_3 = TestRequisition("333") { SERIALIZED_MEASUREMENT_SPEC }

private val MEASUREMENT_SPEC = measurementSpec {
  nonceHashes += TEST_REQUISITION_1.nonceHash
  nonceHashes += TEST_REQUISITION_2.nonceHash
  nonceHashes += TEST_REQUISITION_3.nonceHash
}
private val SERIALIZED_MEASUREMENT_SPEC: ByteString = MEASUREMENT_SPEC.toByteString()

private val REQUISITION_1 =
  TEST_REQUISITION_1.toRequisitionMetadata(Requisition.State.FULFILLED, DUCHY_ONE_NAME).copy {
    path = "foo/123"
  }
private val REQUISITION_2 =
  TEST_REQUISITION_2.toRequisitionMetadata(Requisition.State.FULFILLED, DUCHY_TWO_NAME)
private val REQUISITION_3 =
  TEST_REQUISITION_3.toRequisitionMetadata(Requisition.State.FULFILLED, DUCHY_THREE_NAME)
private val REQUISITIONS = listOf(REQUISITION_1, REQUISITION_2, REQUISITION_3)

private val AGGREGATOR_COMPUTATION_DETAILS =
  ComputationDetails.newBuilder()
    .apply {
      kingdomComputationBuilder.apply {
        publicApiVersion = PUBLIC_API_VERSION
        measurementPublicKey = ENCRYPTION_PUBLIC_KEY.toDuchyEncryptionPublicKey()
        measurementSpec = SERIALIZED_MEASUREMENT_SPEC
      }
      liquidLegionsV2Builder.apply {
        role = RoleInComputation.AGGREGATOR
        parameters = LLV2_PARAMETERS
        addAllParticipant(
          listOf(COMPUTATION_PARTICIPANT_2, COMPUTATION_PARTICIPANT_3, COMPUTATION_PARTICIPANT_1)
        )
        combinedPublicKey = COMBINED_PUBLIC_KEY
        // partiallyCombinedPublicKey and combinedPublicKey are the same at the aggregator.
        partiallyCombinedPublicKey = COMBINED_PUBLIC_KEY
        localElgamalKey = DUCHY_ONE_KEY_PAIR
      }
    }
    .build()

private val NON_AGGREGATOR_COMPUTATION_DETAILS =
  ComputationDetails.newBuilder()
    .apply {
      kingdomComputationBuilder.apply {
        publicApiVersion = PUBLIC_API_VERSION
        measurementPublicKey = ENCRYPTION_PUBLIC_KEY.toDuchyEncryptionPublicKey()
        measurementSpec = SERIALIZED_MEASUREMENT_SPEC
      }
      liquidLegionsV2Builder.apply {
        role = RoleInComputation.NON_AGGREGATOR
        parameters = LLV2_PARAMETERS
        addAllParticipant(
          listOf(COMPUTATION_PARTICIPANT_1, COMPUTATION_PARTICIPANT_2, COMPUTATION_PARTICIPANT_3)
        )
        combinedPublicKey = COMBINED_PUBLIC_KEY
        partiallyCombinedPublicKey = PARTIALLY_COMBINED_PUBLIC_KEY
        localElgamalKey = DUCHY_ONE_KEY_PAIR
      }
    }
    .build()

@RunWith(JUnit4::class)
class LiquidLegionsV2MillTest {
  private val mockLiquidLegionsComputationControl: ComputationControlCoroutineImplBase =
    mockService() {
      onBlocking { advanceComputation(any()) }.thenAnswer {
        val request: Flow<AdvanceComputationRequest> = it.getArgument(0)
        computationControlRequests = runBlocking { request.toList() }
        AdvanceComputationResponse.getDefaultInstance()
      }
    }
  private val mockSystemComputations: SystemComputationsCoroutineImplBase = mockService()
  private val mockComputationParticipants: SystemComputationParticipantsCoroutineImplBase =
    mockService()
  private val mockComputationLogEntries: SystemComputationLogEntriesCoroutineImplBase =
    mockService()
  private val mockComputationStats: ComputationStatsCoroutineImplBase = mockService()
  private val mockCryptoWorker: LiquidLegionsV2Encryption =
    mock(useConstructor = UseConstructor.parameterless()) {
      on { combineElGamalPublicKeys(any()) }.thenAnswer {
        val cryptoRequest: CombineElGamalPublicKeysRequest = it.getArgument(0)
        CombineElGamalPublicKeysResponse.newBuilder()
          .apply {
            elGamalKeysBuilder.apply {
              generator =
                ByteString.copyFromUtf8(
                  cryptoRequest.elGamalKeysList
                    .sortedBy { key -> key.generator.toStringUtf8() }
                    .joinToString(separator = "_") { key -> key.generator.toStringUtf8() }
                )
              element =
                ByteString.copyFromUtf8(
                  cryptoRequest.elGamalKeysList
                    .sortedBy { key -> key.element.toStringUtf8() }
                    .joinToString(separator = "_") { key -> key.element.toStringUtf8() }
                )
            }
          }
          .build()
      }
    }
  private val fakeComputationDb = FakeComputationsDatabase()

  private lateinit var computationDataClients: ComputationDataClients
  private lateinit var computationStore: ComputationStore
  private lateinit var requisitionStore: RequisitionStore

  private val tempDirectory = TemporaryFolder()

  private val blobCount = AtomicInteger()
  private val generatedBlobKeys = mutableListOf<String>()
  private fun generateComputationBlobKey(context: ComputationBlobContext): String {
    return listOf(context.externalComputationId, blobCount.getAndIncrement())
      .joinToString("/")
      .also { generatedBlobKeys.add(it) }
  }
  private fun generateRequisitionBlobKey(context: RequisitionBlobContext): String {
    return listOf(
        context.globalComputationId,
        context.externalRequisitionId,
        blobCount.getAndIncrement()
      )
      .joinToString("/")
      .also { generatedBlobKeys.add(it) }
  }

  private val grpcTestServerRule = GrpcTestServerRule {
    computationStore =
      ComputationStore.forTesting(
        FileSystemStorageClient(tempDirectory.root),
        ::generateComputationBlobKey
      )
    requisitionStore =
      RequisitionStore.forTesting(
        FileSystemStorageClient(tempDirectory.root),
        ::generateRequisitionBlobKey
      )
    computationDataClients =
      ComputationDataClients.forTesting(
        ComputationsCoroutineStub(channel),
        computationStore,
        requisitionStore
      )
    addService(mockLiquidLegionsComputationControl)
    addService(mockSystemComputations)
    addService(mockComputationLogEntries)
    addService(mockComputationParticipants)
    addService(mockComputationStats)
    addService(
      ComputationsService(
        fakeComputationDb,
        systemComputationLogEntriesStub,
        DUCHY_THREE_NAME,
        Clock.systemUTC()
      )
    )
  }

  @get:Rule val ruleChain = chainRulesSequentially(tempDirectory, grpcTestServerRule)

  private val workerStub: ComputationControlCoroutineStub by lazy {
    ComputationControlCoroutineStub(grpcTestServerRule.channel)
  }

  private val systemComputationStub: SystemComputationsCoroutineStub by lazy {
    SystemComputationsCoroutineStub(grpcTestServerRule.channel)
  }

  private val systemComputationLogEntriesStub: SystemComputationLogEntriesCoroutineStub by lazy {
    SystemComputationLogEntriesCoroutineStub(grpcTestServerRule.channel)
  }

  private val systemComputationParticipantsStub:
    SystemComputationParticipantsCoroutineStub by lazy {
    SystemComputationParticipantsCoroutineStub(grpcTestServerRule.channel)
  }

  private val computationStatsStub: ComputationStatsCoroutineStub by lazy {
    ComputationStatsCoroutineStub(grpcTestServerRule.channel)
  }

  private lateinit var computationControlRequests: List<AdvanceComputationRequest>

  // Just use the same workerStub for all other duchies, since it is not relevant to this test.
  private val workerStubs = mapOf(DUCHY_TWO_NAME to workerStub, DUCHY_THREE_NAME to workerStub)

  private lateinit var aggregatorMill: LiquidLegionsV2Mill
  private lateinit var nonAggregatorMill: LiquidLegionsV2Mill

  private fun buildAdvanceComputationRequests(
    globalComputationId: String,
    description: LiquidLegionsV2.Description,
    vararg chunkContents: String
  ): List<AdvanceComputationRequest> {
    val header =
      AdvanceComputationRequest.newBuilder()
        .apply {
          headerBuilder.apply {
            name = ComputationKey(globalComputationId).toName()
            liquidLegionsV2Builder.description = description
          }
        }
        .build()
    val body =
      chunkContents.asList().map {
        AdvanceComputationRequest.newBuilder()
          .apply { bodyChunkBuilder.apply { partialData = ByteString.copyFromUtf8(it) } }
          .build()
      }
    return listOf(header) + body
  }

  @Before
  fun initializeMill() = runBlocking {
    val throttler = MinimumIntervalThrottler(Clock.systemUTC(), Duration.ofSeconds(60))
    val csX509Certificate = readCertificate(CONSENT_SIGNALING_CERT_DER)
    val csSigningKey =
      SigningKeyHandle(
        csX509Certificate,
        readPrivateKey(CONSENT_SIGNALING_PRIVATE_KEY_DER, csX509Certificate.publicKey.algorithm)
      )
    val csCertificate = Certificate(CONSENT_SIGNALING_CERT_NAME, csX509Certificate)

    aggregatorMill =
      LiquidLegionsV2Mill(
        millId = MILL_ID,
        duchyId = DUCHY_ONE_NAME,
        signingKey = csSigningKey,
        consentSignalCert = csCertificate,
        dataClients = computationDataClients,
        systemComputationParticipantsClient = systemComputationParticipantsStub,
        systemComputationsClient = systemComputationStub,
        systemComputationLogEntriesClient = systemComputationLogEntriesStub,
        computationStatsClient = computationStatsStub,
        workerStubs = workerStubs,
        cryptoWorker = mockCryptoWorker,
        throttler = throttler,
        requestChunkSizeBytes = 20,
        maximumAttempts = 2
      )
    nonAggregatorMill =
      LiquidLegionsV2Mill(
        millId = MILL_ID,
        duchyId = DUCHY_ONE_NAME,
        signingKey = csSigningKey,
        consentSignalCert = csCertificate,
        dataClients = computationDataClients,
        systemComputationParticipantsClient = systemComputationParticipantsStub,
        systemComputationsClient = systemComputationStub,
        systemComputationLogEntriesClient = systemComputationLogEntriesStub,
        computationStatsClient = computationStatsStub,
        workerStubs = workerStubs,
        cryptoWorker = mockCryptoWorker,
        throttler = throttler,
        requestChunkSizeBytes = 20,
        maximumAttempts = 2
      )
  }

  @Test
  fun `exceeding max attempt should fail the computation`() = runBlocking {
    // Stage 0. preparing the database and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = INITIALIZATION_PHASE.toProtocolStage()
        )
        .build()

    val initialComputationDetails =
      NON_AGGREGATOR_COMPUTATION_DETAILS
        .toBuilder()
        .apply {
          liquidLegionsV2Builder.apply {
            parametersBuilder.ellipticCurveId = CURVE_ID.toInt()
            clearPartiallyCombinedPublicKey()
            clearCombinedPublicKey()
            clearLocalElgamalKey()
          }
        }
        .build()

    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = initialComputationDetails,
      requisitions = REQUISITIONS
    )

    whenever(mockCryptoWorker.completeInitializationPhase(any())).thenAnswer {
      CompleteInitializationPhaseResponse.newBuilder()
        .apply {
          elGamalKeyPairBuilder.apply {
            publicKeyBuilder.apply {
              generator = ByteString.copyFromUtf8("generator-foo")
              element = ByteString.copyFromUtf8("element-foo")
            }
            secretKey = ByteString.copyFromUtf8("secretKey-foo")
          }
        }
        .build()
    }

    // This will result in TRANSIENT gRPC failure.
    whenever(mockComputationParticipants.setParticipantRequisitionParams(any()))
      .thenThrow(Status.UNKNOWN.asRuntimeException())

    // First attempt fails, which doesn't change the computation stage.
    nonAggregatorMill.pollAndProcessNextComputation()

    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = INITIALIZATION_PHASE.toProtocolStage()
            version = 3 // claimTask + updateComputationDetails + enqueueComputation
            computationDetails =
              initialComputationDetails
                .toBuilder()
                .apply {
                  liquidLegionsV2Builder.localElgamalKeyBuilder.apply {
                    publicKeyBuilder.apply {
                      generator = ByteString.copyFromUtf8("generator-foo")
                      element = ByteString.copyFromUtf8("element-foo")
                    }
                    secretKey = ByteString.copyFromUtf8("secretKey-foo")
                  }
                }
                .build()
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )

    // Second attempt fails, which will fail the computation.
    nonAggregatorMill.pollAndProcessNextComputation()

    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 2
            computationStage = COMPLETE.toProtocolStage()
            version = 5 // claimTask + updateComputationDetails + enqueueComputation + claimTask +
            // EndComputation
            computationDetails =
              initialComputationDetails
                .toBuilder()
                .apply {
                  endingState = CompletedReason.FAILED
                  liquidLegionsV2Builder.localElgamalKeyBuilder.apply {
                    publicKeyBuilder.apply {
                      generator = ByteString.copyFromUtf8("generator-foo")
                      element = ByteString.copyFromUtf8("element-foo")
                    }
                    secretKey = ByteString.copyFromUtf8("secretKey-foo")
                  }
                }
                .build()
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )
  }

  @Test
  fun `initialization phase`() = runBlocking {
    // Stage 0. preparing the database and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = INITIALIZATION_PHASE.toProtocolStage()
        )
        .build()

    val initialComputationDetails =
      NON_AGGREGATOR_COMPUTATION_DETAILS
        .toBuilder()
        .apply {
          liquidLegionsV2Builder.apply {
            parametersBuilder.ellipticCurveId = CURVE_ID.toInt()
            clearPartiallyCombinedPublicKey()
            clearCombinedPublicKey()
            clearLocalElgamalKey()
          }
        }
        .build()

    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = initialComputationDetails,
      requisitions = REQUISITIONS
    )

    var cryptoRequest = CompleteInitializationPhaseRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeInitializationPhase(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      CompleteInitializationPhaseResponse.newBuilder()
        .apply {
          elGamalKeyPairBuilder.apply {
            publicKeyBuilder.apply {
              generator = ByteString.copyFromUtf8("generator-foo")
              element = ByteString.copyFromUtf8("element-foo")
            }
            secretKey = ByteString.copyFromUtf8("secretKey-foo")
          }
        }
        .build()
    }

    // Stage 1. Process the above computation
    nonAggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = WAIT_REQUISITIONS_AND_KEY_SET.toProtocolStage()
            version = 3 // claimTask + updateComputationDetails + transitionStage
            computationDetails =
              initialComputationDetails
                .toBuilder()
                .apply {
                  liquidLegionsV2Builder.localElgamalKeyBuilder.apply {
                    publicKeyBuilder.apply {
                      generator = ByteString.copyFromUtf8("generator-foo")
                      element = ByteString.copyFromUtf8("element-foo")
                    }
                    secretKey = ByteString.copyFromUtf8("secretKey-foo")
                  }
                }
                .build()
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )

    verifyProtoArgument(
        mockComputationParticipants,
        SystemComputationParticipantsCoroutineImplBase::setParticipantRequisitionParams
      )
      .comparingExpectedFieldsOnly()
      .isEqualTo(
        SetParticipantRequisitionParamsRequest.newBuilder()
          .apply {
            name = ComputationParticipantKey(GLOBAL_ID, DUCHY_ONE_NAME).toName()
            requisitionParamsBuilder.apply {
              duchyCertificate = CONSENT_SIGNALING_CERT_NAME
              liquidLegionsV2Builder.apply {
                elGamalPublicKey = CONSENT_SIGNALING_EL_GAMAL_PUBLIC_KEY.toByteString()
              }
            }
          }
          .build()
      )

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteInitializationPhaseRequest.newBuilder().apply { curveId = CURVE_ID }.build()
      )
  }

  @Test
  fun `confirmation phase, failed due to missing local requisition`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val requisition1 = REQUISITION_1
    // requisition2 is fulfilled at Duchy One, but doesn't have path set.
    val requisition2 =
      REQUISITION_2.copy { details = details.copy { externalFulfillingDuchyId = DUCHY_ONE_NAME } }
    val computationDetailsWithoutPublicKey =
      AGGREGATOR_COMPUTATION_DETAILS
        .toBuilder()
        .apply { liquidLegionsV2Builder.clearCombinedPublicKey().clearPartiallyCombinedPublicKey() }
        .build()
    fakeComputationDb.addComputation(
      globalId = GLOBAL_ID,
      stage = CONFIRMATION_PHASE.toProtocolStage(),
      computationDetails = computationDetailsWithoutPublicKey,
      requisitions = listOf(requisition1, requisition2)
    )

    whenever(mockComputationLogEntries.createComputationLogEntry(any()))
      .thenReturn(ComputationLogEntry.getDefaultInstance())

    // Stage 1. Process the above computation
    aggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    assertThat(fakeComputationDb[LOCAL_ID]!!)
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = COMPLETE.toProtocolStage()
            version = 2 // claimTask + transitionStage
            computationDetails =
              computationDetailsWithoutPublicKey
                .toBuilder()
                .apply { endingState = CompletedReason.FAILED }
                .build()
            addAllRequisitions(listOf(requisition1, requisition2))
          }
          .build()
      )

    verifyProtoArgument(
        mockComputationParticipants,
        SystemComputationParticipantsCoroutineImplBase::failComputationParticipant
      )
      .comparingExpectedFieldsOnly()
      .isEqualTo(
        FailComputationParticipantRequest.newBuilder()
          .apply {
            name = ComputationParticipantKey(GLOBAL_ID, DUCHY_ONE_NAME).toName()
            failureBuilder.apply {
              participantChildReferenceId = MILL_ID
              errorMessage =
                "java.lang.Exception: @Mill a nice mill, Computation 1234 failed due to:\n" +
                  "Cannot verify participation of all DataProviders.\n" +
                  "Missing expected data for requisition 222."
              stageAttemptBuilder.apply {
                stage = CONFIRMATION_PHASE.number
                stageName = CONFIRMATION_PHASE.name
                attemptNumber = 1
              }
            }
          }
          .build()
      )
  }

  @Test
  fun `confirmation phase, passed at non-aggregator`() =
    runBlocking<Unit> {
      // Stage 0. preparing the storage and set up mock
      val computationDetailsWithoutPublicKey =
        NON_AGGREGATOR_COMPUTATION_DETAILS
          .toBuilder()
          .apply {
            liquidLegionsV2Builder.clearCombinedPublicKey().clearPartiallyCombinedPublicKey()
          }
          .build()
      fakeComputationDb.addComputation(
        globalId = GLOBAL_ID,
        stage = CONFIRMATION_PHASE.toProtocolStage(),
        computationDetails = computationDetailsWithoutPublicKey,
        requisitions = REQUISITIONS
      )

      whenever(mockComputationLogEntries.createComputationLogEntry(any()))
        .thenReturn(ComputationLogEntry.getDefaultInstance())

      // Stage 1. Process the above computation
      aggregatorMill.pollAndProcessNextComputation()

      // Stage 2. Check the status of the computation
      assertThat(fakeComputationDb[LOCAL_ID]!!)
        .isEqualTo(
          ComputationToken.newBuilder()
            .apply {
              globalComputationId = GLOBAL_ID
              localComputationId = LOCAL_ID
              attempt = 1
              computationStage = WAIT_TO_START.toProtocolStage()
              version = 3 // claimTask + updateComputationDetail + transitionStage
              computationDetails =
                NON_AGGREGATOR_COMPUTATION_DETAILS
                  .toBuilder()
                  .apply {
                    liquidLegionsV2Builder.apply {
                      combinedPublicKey = COMBINED_PUBLIC_KEY
                      partiallyCombinedPublicKey = PARTIALLY_COMBINED_PUBLIC_KEY
                    }
                  }
                  .build()
              addAllRequisitions(REQUISITIONS)
            }
            .build()
        )

      verifyProtoArgument(
          mockComputationParticipants,
          SystemComputationParticipantsCoroutineImplBase::confirmComputationParticipant
        )
        .isEqualTo(
          ConfirmComputationParticipantRequest.newBuilder()
            .apply { name = ComputationParticipantKey(GLOBAL_ID, DUCHY_ONE_NAME).toName() }
            .build()
        )
    }

  @Test
  fun `confirmation phase, passed at aggregator`() =
    runBlocking<Unit> {
      // Stage 0. preparing the storage and set up mock
      val computationDetailsWithoutPublicKey =
        AGGREGATOR_COMPUTATION_DETAILS
          .toBuilder()
          .apply {
            liquidLegionsV2Builder.clearCombinedPublicKey().clearPartiallyCombinedPublicKey()
          }
          .build()
      fakeComputationDb.addComputation(
        globalId = GLOBAL_ID,
        stage = CONFIRMATION_PHASE.toProtocolStage(),
        computationDetails = computationDetailsWithoutPublicKey,
        requisitions = REQUISITIONS
      )

      whenever(mockComputationLogEntries.createComputationLogEntry(any()))
        .thenReturn(ComputationLogEntry.getDefaultInstance())

      // Stage 1. Process the above computation
      aggregatorMill.pollAndProcessNextComputation()

      // Stage 2. Check the status of the computation
      assertThat(fakeComputationDb[LOCAL_ID]!!)
        .isEqualTo(
          ComputationToken.newBuilder()
            .apply {
              globalComputationId = GLOBAL_ID
              localComputationId = LOCAL_ID
              attempt = 1
              computationStage = WAIT_SETUP_PHASE_INPUTS.toProtocolStage()
              version = 3 // claimTask + updateComputationDetails + transitionStage
              addAllBlobs(listOf(newEmptyOutputBlobMetadata(0), newEmptyOutputBlobMetadata(1)))
              stageSpecificDetailsBuilder.apply {
                liquidLegionsV2Builder.waitSetupPhaseInputsDetailsBuilder.apply {
                  putExternalDuchyLocalBlobId("DUCHY_TWO", 0L)
                  putExternalDuchyLocalBlobId("DUCHY_THREE", 1L)
                }
              }
              computationDetails =
                AGGREGATOR_COMPUTATION_DETAILS
                  .toBuilder()
                  .apply {
                    liquidLegionsV2Builder.apply {
                      combinedPublicKey = COMBINED_PUBLIC_KEY
                      partiallyCombinedPublicKey = COMBINED_PUBLIC_KEY
                    }
                  }
                  .build()
              addAllRequisitions(REQUISITIONS)
            }
            .build()
        )

      verifyProtoArgument(
          mockComputationParticipants,
          SystemComputationParticipantsCoroutineImplBase::confirmComputationParticipant
        )
        .isEqualTo(
          ConfirmComputationParticipantRequest.newBuilder()
            .apply { name = ComputationParticipantKey(GLOBAL_ID, DUCHY_ONE_NAME).toName() }
            .build()
        )
    }

  @Test
  fun `confirmation phase, failed due to invalid nonce and ElGamal key signature`() =
    runBlocking<Unit> {
      // Stage 0. preparing the storage and set up mock
      val computationDetailsWithoutInvalidDuchySignature =
        AGGREGATOR_COMPUTATION_DETAILS
          .toBuilder()
          .apply {
            liquidLegionsV2Builder.apply {
              participantBuilderList[0].apply {
                elGamalPublicKeySignature = ByteString.copyFromUtf8("An invalid signature")
              }
            }
          }
          .build()
      val requisitionWithInvalidNonce =
        REQUISITION_1.copy { details = details.copy { nonce = 404L } }
      fakeComputationDb.addComputation(
        globalId = GLOBAL_ID,
        stage = CONFIRMATION_PHASE.toProtocolStage(),
        computationDetails = computationDetailsWithoutInvalidDuchySignature,
        requisitions = listOf(requisitionWithInvalidNonce)
      )

      whenever(mockComputationLogEntries.createComputationLogEntry(any()))
        .thenReturn(ComputationLogEntry.getDefaultInstance())

      // Stage 1. Process the above computation
      aggregatorMill.pollAndProcessNextComputation()

      // Stage 2. Check the status of the computation
      assertThat(fakeComputationDb[LOCAL_ID]!!)
        .isEqualTo(
          ComputationToken.newBuilder()
            .apply {
              globalComputationId = GLOBAL_ID
              localComputationId = LOCAL_ID
              attempt = 1
              computationStage = COMPLETE.toProtocolStage()
              version = 2 // claimTask + transitionStage
              computationDetails =
                computationDetailsWithoutInvalidDuchySignature
                  .toBuilder()
                  .apply { endingState = CompletedReason.FAILED }
                  .build()
              addAllRequisitions(listOf(requisitionWithInvalidNonce))
            }
            .build()
        )

      verifyProtoArgument(
          mockComputationParticipants,
          SystemComputationParticipantsCoroutineImplBase::failComputationParticipant
        )
        .comparingExpectedFieldsOnly()
        .isEqualTo(
          FailComputationParticipantRequest.newBuilder()
            .apply {
              name = ComputationParticipantKey(GLOBAL_ID, DUCHY_ONE_NAME).toName()
              failureBuilder.apply {
                participantChildReferenceId = MILL_ID
                errorMessage =
                  "java.lang.Exception: @Mill a nice mill, Computation 1234 failed due to:\n" +
                    "Cannot verify participation of all DataProviders.\n" +
                    "ElGamalPublicKey signature of DUCHY_TWO is invalid."
                stageAttemptBuilder.apply {
                  stage = CONFIRMATION_PHASE.number
                  stageName = CONFIRMATION_PHASE.name
                  attemptNumber = 1
                }
              }
            }
            .build()
        )
    }

  @Test
  fun `setup phase at non-aggregator using calculated result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = SETUP_PHASE.toProtocolStage()
        )
        .build()
    requisitionStore.writeString(
      RequisitionBlobContext(GLOBAL_ID, REQUISITION_1.externalKey.externalRequisitionId),
      "local_requisition"
    )
    val requisitionListWithCorrectPath =
      listOf(REQUISITION_1.copy { path = generatedBlobKeys.last() }, REQUISITION_2, REQUISITION_3)
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS,
      requisitions = requisitionListWithCorrectPath,
      blobs = listOf(newEmptyOutputBlobMetadata(1L))
    )

    var cryptoRequest = CompleteSetupPhaseRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeSetupPhase(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      val postFix = ByteString.copyFromUtf8("-completeSetupPhase-done")
      CompleteSetupPhaseResponse.newBuilder()
        .apply { combinedRegisterVector = cryptoRequest.combinedRegisterVector.concat(postFix) }
        .build()
    }

    // Stage 1. Process the above computation
    nonAggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    val blobKey = generatedBlobKeys.last()
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = WAIT_EXECUTION_PHASE_ONE_INPUTS.toProtocolStage()
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.INPUT
              blobId = 0
              path = blobKey
            }
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.OUTPUT
              blobId = 1
            }
            version = 3 // claimTask + writeOutputBlob + transitionStage
            computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS
            addAllRequisitions(requisitionListWithCorrectPath)
          }
          .build()
      )

    assertThat(computationStore.get(blobKey)?.readToString())
      .isEqualTo("local_requisition-completeSetupPhase-done")

    assertThat(computationControlRequests)
      .containsExactlyElementsIn(
        buildAdvanceComputationRequests(
          GLOBAL_ID,
          SETUP_PHASE_INPUT,
          "local_requisition-co",
          "mpleteSetupPhase-don",
          "e"
        )
      )
      .inOrder()

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteSetupPhaseRequest.newBuilder()
          .apply {
            combinedRegisterVector = ByteString.copyFromUtf8("local_requisition")
            noiseParametersBuilder.apply {
              compositeElGamalPublicKey = COMBINED_PUBLIC_KEY
              curveId = CURVE_ID
              contributorsCount = WORKER_COUNT
              totalSketchesCount = REQUISITIONS.size
              dpParamsBuilder.apply {
                blindHistogram = TEST_NOISE_CONFIG.reachNoiseConfig.blindHistogramNoise
                noiseForPublisherNoise = TEST_NOISE_CONFIG.reachNoiseConfig.noiseForPublisherNoise
                globalReachDpNoise = TEST_NOISE_CONFIG.reachNoiseConfig.globalReachDpNoise
              }
            }
          }
          .build()
      )
  }

  @Test
  fun `setup phase at aggregator using calculated result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = SETUP_PHASE.toProtocolStage()
        )
        .build()
    requisitionStore.writeString(
      RequisitionBlobContext(GLOBAL_ID, REQUISITION_1.externalKey.externalRequisitionId),
      "local_requisition_"
    )
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, SETUP_PHASE.toProtocolStage()),
      "duchy_two_sketch_"
    )
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, SETUP_PHASE.toProtocolStage()),
      "duchy_three_sketch_"
    )
    val requisitionListWithCorrectPath =
      listOf(REQUISITION_1.copy { path = generatedBlobKeys[0] }, REQUISITION_2, REQUISITION_3)
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = AGGREGATOR_COMPUTATION_DETAILS,
      blobs =
        listOf(
          newInputBlobMetadata(0L, generatedBlobKeys[1]),
          newInputBlobMetadata(1L, generatedBlobKeys[2]),
          newEmptyOutputBlobMetadata(3L)
        ),
      requisitions = requisitionListWithCorrectPath
    )

    var cryptoRequest = CompleteSetupPhaseRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeSetupPhase(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      val postFix = ByteString.copyFromUtf8("-completeSetupPhase-done")
      CompleteSetupPhaseResponse.newBuilder()
        .apply { combinedRegisterVector = cryptoRequest.combinedRegisterVector.concat(postFix) }
        .build()
    }

    // Stage 1. Process the above computation
    aggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    val blobKey = generatedBlobKeys.last()
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = WAIT_EXECUTION_PHASE_ONE_INPUTS.toProtocolStage()
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.INPUT
              blobId = 0
              path = blobKey
            }
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.OUTPUT
              blobId = 1
            }
            version = 3 // claimTask + writeOutputBlob + transitionStage
            computationDetails = AGGREGATOR_COMPUTATION_DETAILS
            addAllRequisitions(requisitionListWithCorrectPath)
          }
          .build()
      )

    assertThat(computationStore.get(blobKey)?.readToString())
      .isEqualTo("local_requisition_duchy_two_sketch_duchy_three_sketch_-completeSetupPhase-done")

    assertThat(computationControlRequests)
      .containsExactlyElementsIn(
        buildAdvanceComputationRequests(
          GLOBAL_ID,
          EXECUTION_PHASE_ONE_INPUT,
          "local_requisition_du",
          "chy_two_sketch_duchy",
          "_three_sketch_-compl",
          "eteSetupPhase-done"
        )
      )
      .inOrder()

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteSetupPhaseRequest.newBuilder()
          .apply {
            combinedRegisterVector =
              ByteString.copyFromUtf8("local_requisition_duchy_two_sketch_duchy_three_sketch_")
            noiseParametersBuilder.apply {
              compositeElGamalPublicKey = COMBINED_PUBLIC_KEY
              curveId = CURVE_ID
              contributorsCount = WORKER_COUNT
              totalSketchesCount = REQUISITIONS.size
              dpParamsBuilder.apply {
                blindHistogram = TEST_NOISE_CONFIG.reachNoiseConfig.blindHistogramNoise
                noiseForPublisherNoise = TEST_NOISE_CONFIG.reachNoiseConfig.noiseForPublisherNoise
                globalReachDpNoise = TEST_NOISE_CONFIG.reachNoiseConfig.globalReachDpNoise
              }
            }
          }
          .build()
      )
  }

  @Test
  fun `execution phase one at non-aggregater using cached result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = EXECUTION_PHASE_ONE.toProtocolStage()
        )
        .build()
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, EXECUTION_PHASE_ONE.toProtocolStage()),
      "sketch"
    )
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, EXECUTION_PHASE_ONE.toProtocolStage()),
      "cached result"
    )
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS,
      blobs =
        listOf(
          newInputBlobMetadata(0L, generatedBlobKeys[0]),
          newOutputBlobMetadata(1L, generatedBlobKeys[1])
        ),
      requisitions = REQUISITIONS
    )

    // Stage 1. Process the above computation
    nonAggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = WAIT_EXECUTION_PHASE_TWO_INPUTS.toProtocolStage()
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.INPUT
              blobId = 0
              path = generatedBlobKeys.last()
            }
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.OUTPUT
              blobId = 1
            }
            version = 2 // claimTask + transitionStage
            computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )

    assertThat(computationControlRequests)
      .containsExactlyElementsIn(
        buildAdvanceComputationRequests(GLOBAL_ID, EXECUTION_PHASE_ONE_INPUT, "cached result")
      )
      .inOrder()
  }

  @Test
  fun `execution phase one at non-aggregater using calculated result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = EXECUTION_PHASE_ONE.toProtocolStage()
        )
        .build()
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, EXECUTION_PHASE_ONE.toProtocolStage()),
      "data"
    )
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS,
      blobs =
        listOf(newInputBlobMetadata(0L, generatedBlobKeys.last()), newEmptyOutputBlobMetadata(1L)),
      requisitions = REQUISITIONS
    )

    var cryptoRequest = CompleteExecutionPhaseOneRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeExecutionPhaseOne(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      val postFix = ByteString.copyFromUtf8("-completeExecutionPhaseOne-done")
      CompleteExecutionPhaseOneResponse.newBuilder()
        .apply { combinedRegisterVector = cryptoRequest.combinedRegisterVector.concat(postFix) }
        .build()
    }

    // Stage 1. Process the above computation
    nonAggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    val blobKey = generatedBlobKeys.last()
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = WAIT_EXECUTION_PHASE_TWO_INPUTS.toProtocolStage()
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.INPUT
              blobId = 0
              path = blobKey
            }
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.OUTPUT
              blobId = 1
            }
            version = 3 // claimTask + writeOutputBlob + transitionStage
            computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )
    assertThat(computationStore.get(blobKey)?.readToString())
      .isEqualTo("data-completeExecutionPhaseOne-done")

    assertThat(computationControlRequests)
      .containsExactlyElementsIn(
        buildAdvanceComputationRequests(
          GLOBAL_ID,
          EXECUTION_PHASE_ONE_INPUT,
          "data-completeExecuti", // Chunk 1, size 20
          "onPhaseOne-done" // Chunk 2, the rest
        )
      )
      .inOrder()

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteExecutionPhaseOneRequest.newBuilder()
          .apply {
            combinedRegisterVector = ByteString.copyFromUtf8("data")
            localElGamalKeyPair = DUCHY_ONE_KEY_PAIR
            compositeElGamalPublicKey = COMBINED_PUBLIC_KEY
            curveId = CURVE_ID
          }
          .build()
      )
  }

  @Test
  fun `execution phase one at aggregater using calculated result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = EXECUTION_PHASE_ONE.toProtocolStage()
        )
        .build()
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, EXECUTION_PHASE_ONE.toProtocolStage()),
      "data"
    )
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = AGGREGATOR_COMPUTATION_DETAILS,
      blobs =
        listOf(newInputBlobMetadata(0L, generatedBlobKeys.last()), newEmptyOutputBlobMetadata(1L)),
      requisitions = REQUISITIONS
    )

    var cryptoRequest = CompleteExecutionPhaseOneAtAggregatorRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeExecutionPhaseOneAtAggregator(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      val postFix = ByteString.copyFromUtf8("-completeExecutionPhaseOneAtAggregator-done")
      CompleteExecutionPhaseOneAtAggregatorResponse.newBuilder()
        .apply { flagCountTuples = cryptoRequest.combinedRegisterVector.concat(postFix) }
        .build()
    }

    // Stage 1. Process the above computation
    aggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    val blobKey = generatedBlobKeys.last()
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = WAIT_EXECUTION_PHASE_TWO_INPUTS.toProtocolStage()
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.INPUT
              blobId = 0
              path = blobKey
            }
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.OUTPUT
              blobId = 1
            }
            version = 3 // claimTask + writeOutputBlob + transitionStage
            computationDetails = AGGREGATOR_COMPUTATION_DETAILS
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )
    assertThat(computationStore.get(blobKey)?.readToString())
      .isEqualTo("data-completeExecutionPhaseOneAtAggregator-done")

    assertThat(computationControlRequests)
      .containsExactlyElementsIn(
        buildAdvanceComputationRequests(
          GLOBAL_ID,
          EXECUTION_PHASE_TWO_INPUT,
          "data-completeExecuti", // Chunk 1, size 20
          "onPhaseOneAtAggregat", // Chunk 2, size 20
          "or-done" // Chunk 3, the rest
        )
      )
      .inOrder()

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteExecutionPhaseOneAtAggregatorRequest.newBuilder()
          .apply {
            combinedRegisterVector = ByteString.copyFromUtf8("data")
            localElGamalKeyPair = DUCHY_ONE_KEY_PAIR
            compositeElGamalPublicKey = COMBINED_PUBLIC_KEY
            curveId = CURVE_ID
            noiseParametersBuilder.apply {
              maximumFrequency = MAX_FREQUENCY
              contributorsCount = WORKER_COUNT
              dpParams = TEST_NOISE_CONFIG.frequencyNoiseConfig
            }
            totalSketchesCount = REQUISITIONS.size
          }
          .build()
      )
  }

  @Test
  fun `execution phase two at non-aggregater using calculated result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = EXECUTION_PHASE_TWO.toProtocolStage()
        )
        .build()
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, EXECUTION_PHASE_TWO.toProtocolStage()),
      "data"
    )
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS,
      blobs =
        listOf(newInputBlobMetadata(0L, generatedBlobKeys.last()), newEmptyOutputBlobMetadata(1L)),
      requisitions = REQUISITIONS
    )

    var cryptoRequest = CompleteExecutionPhaseTwoRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeExecutionPhaseTwo(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      val postFix = ByteString.copyFromUtf8("-completeExecutionPhaseTwo-done")
      CompleteExecutionPhaseTwoResponse.newBuilder()
        .apply { flagCountTuples = cryptoRequest.flagCountTuples.concat(postFix) }
        .build()
    }

    // Stage 1. Process the above computation
    nonAggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    val blobKey = generatedBlobKeys.last()
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = WAIT_EXECUTION_PHASE_THREE_INPUTS.toProtocolStage()
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.INPUT
              blobId = 0
              path = blobKey
            }
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.OUTPUT
              blobId = 1
            }
            version = 3 // claimTask + writeOutputBlob + transitionStage
            computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )
    assertThat(computationStore.get(blobKey)?.readToString())
      .isEqualTo("data-completeExecutionPhaseTwo-done")

    assertThat(computationControlRequests)
      .containsExactlyElementsIn(
        buildAdvanceComputationRequests(
          GLOBAL_ID,
          EXECUTION_PHASE_TWO_INPUT,
          "data-completeExecuti", // Chunk 1, size 20
          "onPhaseTwo-done" // Chunk 2, the rest
        )
      )
      .inOrder()

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteExecutionPhaseTwoRequest.newBuilder()
          .apply {
            flagCountTuples = ByteString.copyFromUtf8("data")
            localElGamalKeyPair = DUCHY_ONE_KEY_PAIR
            compositeElGamalPublicKey = COMBINED_PUBLIC_KEY
            partialCompositeElGamalPublicKey = PARTIALLY_COMBINED_PUBLIC_KEY
            curveId = CURVE_ID
            noiseParametersBuilder.apply {
              maximumFrequency = MAX_FREQUENCY
              contributorsCount = WORKER_COUNT
              dpParams = TEST_NOISE_CONFIG.frequencyNoiseConfig
            }
          }
          .build()
      )
  }

  @Test
  fun `execution phase two at aggregater using calculated result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = EXECUTION_PHASE_TWO.toProtocolStage()
        )
        .build()
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, EXECUTION_PHASE_TWO.toProtocolStage()),
      "data"
    )
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = AGGREGATOR_COMPUTATION_DETAILS,
      blobs =
        listOf(newInputBlobMetadata(0L, generatedBlobKeys.last()), newEmptyOutputBlobMetadata(1L)),
      requisitions = REQUISITIONS
    )

    val testReach = 123L
    var cryptoRequest = CompleteExecutionPhaseTwoAtAggregatorRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeExecutionPhaseTwoAtAggregator(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      val postFix = ByteString.copyFromUtf8("-completeExecutionPhaseTwoAtAggregator-done")
      CompleteExecutionPhaseTwoAtAggregatorResponse.newBuilder()
        .apply {
          sameKeyAggregatorMatrix = cryptoRequest.flagCountTuples.concat(postFix)
          reach = testReach
        }
        .build()
    }

    // Stage 1. Process the above computation
    aggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    val blobKey = generatedBlobKeys.last()
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = WAIT_EXECUTION_PHASE_THREE_INPUTS.toProtocolStage()
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.INPUT
              blobId = 0
              path = blobKey
            }
            addBlobsBuilder().apply {
              dependencyType = ComputationBlobDependency.OUTPUT
              blobId = 1
            }
            version = 4 // claimTask + writeOutputBlob + ComputationDetails + transitionStage
            computationDetails =
              AGGREGATOR_COMPUTATION_DETAILS
                .toBuilder()
                .apply { liquidLegionsV2Builder.reachEstimateBuilder.reach = testReach }
                .build()
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )
    assertThat(computationStore.get(blobKey)?.readToString())
      .isEqualTo("data-completeExecutionPhaseTwoAtAggregator-done")

    assertThat(computationControlRequests)
      .containsExactlyElementsIn(
        buildAdvanceComputationRequests(
          GLOBAL_ID,
          EXECUTION_PHASE_THREE_INPUT,
          "data-completeExecuti", // Chunk 1, size 20
          "onPhaseTwoAtAggregat", // Chunk 2, size 20
          "or-done" // Chunk 3, the rest
        )
      )
      .inOrder()

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteExecutionPhaseTwoAtAggregatorRequest.newBuilder()
          .apply {
            flagCountTuples = ByteString.copyFromUtf8("data")
            localElGamalKeyPair = DUCHY_ONE_KEY_PAIR
            compositeElGamalPublicKey = COMBINED_PUBLIC_KEY
            curveId = CURVE_ID
            maximumFrequency = MAX_FREQUENCY
            liquidLegionsParametersBuilder.apply {
              decayRate = DECAY_RATE
              size = SKETCH_SIZE
            }
            reachDpNoiseBaselineBuilder.apply {
              contributorsCount = WORKER_COUNT
              globalReachDpNoise = TEST_NOISE_CONFIG.reachNoiseConfig.globalReachDpNoise
            }
            frequencyNoiseParametersBuilder.apply {
              contributorsCount = WORKER_COUNT
              maximumFrequency = MAX_FREQUENCY
              dpParams = TEST_NOISE_CONFIG.frequencyNoiseConfig
            }
          }
          .build()
      )
  }

  @Test
  fun `execution phase three at non-aggregater using calculated result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = EXECUTION_PHASE_THREE.toProtocolStage()
        )
        .build()
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, EXECUTION_PHASE_THREE.toProtocolStage()),
      "data"
    )
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = NON_AGGREGATOR_COMPUTATION_DETAILS,
      blobs =
        listOf(newInputBlobMetadata(0L, generatedBlobKeys.last()), newEmptyOutputBlobMetadata(1L)),
      requisitions = REQUISITIONS
    )

    var cryptoRequest = CompleteExecutionPhaseThreeRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeExecutionPhaseThree(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      val postFix = ByteString.copyFromUtf8("-completeExecutionPhaseThree-done")
      CompleteExecutionPhaseThreeResponse.newBuilder()
        .apply { sameKeyAggregatorMatrix = cryptoRequest.sameKeyAggregatorMatrix.concat(postFix) }
        .build()
    }

    // Stage 1. Process the above computation
    nonAggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    val blobKey = generatedBlobKeys.last()
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = COMPLETE.toProtocolStage()
            version = 3 // claimTask + writeOutputBlob + transitionStage
            computationDetails =
              NON_AGGREGATOR_COMPUTATION_DETAILS
                .toBuilder()
                .apply { endingState = CompletedReason.SUCCEEDED }
                .build()
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )
    assertThat(computationStore.get(blobKey)?.readToString())
      .isEqualTo("data-completeExecutionPhaseThree-done")

    assertThat(computationControlRequests)
      .containsExactlyElementsIn(
        buildAdvanceComputationRequests(
          GLOBAL_ID,
          EXECUTION_PHASE_THREE_INPUT,
          "data-completeExecuti", // Chunk 1, size 20
          "onPhaseThree-done" // Chunk 2, the rest
        )
      )
      .inOrder()

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteExecutionPhaseThreeRequest.newBuilder()
          .apply {
            sameKeyAggregatorMatrix = ByteString.copyFromUtf8("data")
            localElGamalKeyPair = DUCHY_ONE_KEY_PAIR
            curveId = CURVE_ID
          }
          .build()
      )
  }

  @Test
  fun `execution phase three at aggregater using calculated result`() = runBlocking {
    // Stage 0. preparing the storage and set up mock
    val partialToken =
      FakeComputationsDatabase.newPartialToken(
          localId = LOCAL_ID,
          stage = EXECUTION_PHASE_THREE.toProtocolStage()
        )
        .build()
    val computationDetailsWithReach =
      AGGREGATOR_COMPUTATION_DETAILS
        .toBuilder()
        .apply { liquidLegionsV2Builder.apply { reachEstimateBuilder.reach = 123 } }
        .build()
    computationStore.writeString(
      ComputationBlobContext(GLOBAL_ID, EXECUTION_PHASE_THREE.toProtocolStage()),
      "data"
    )
    fakeComputationDb.addComputation(
      partialToken.localComputationId,
      partialToken.computationStage,
      computationDetails = computationDetailsWithReach,
      blobs =
        listOf(newInputBlobMetadata(0L, generatedBlobKeys.last()), newEmptyOutputBlobMetadata(1L)),
      requisitions = REQUISITIONS
    )

    var cryptoRequest = CompleteExecutionPhaseThreeAtAggregatorRequest.getDefaultInstance()
    whenever(mockCryptoWorker.completeExecutionPhaseThreeAtAggregator(any())).thenAnswer {
      cryptoRequest = it.getArgument(0)
      CompleteExecutionPhaseThreeAtAggregatorResponse.newBuilder()
        .putAllFrequencyDistribution(mapOf(1L to 0.3, 2L to 0.7))
        .build()
    }

    var systemComputationResult = SetComputationResultRequest.getDefaultInstance()
    whenever(mockSystemComputations.setComputationResult(any())).thenAnswer {
      systemComputationResult = it.getArgument(0)
      Computation.getDefaultInstance()
    }

    // Stage 1. Process the above computation
    aggregatorMill.pollAndProcessNextComputation()

    // Stage 2. Check the status of the computation
    val blobKey = generatedBlobKeys.last()
    assertThat(fakeComputationDb[LOCAL_ID])
      .isEqualTo(
        ComputationToken.newBuilder()
          .apply {
            globalComputationId = GLOBAL_ID
            localComputationId = LOCAL_ID
            attempt = 1
            computationStage = COMPLETE.toProtocolStage()
            version = 3 // claimTask + writeOutputBlob + transitionStage
            computationDetails =
              computationDetailsWithReach
                .toBuilder()
                .apply { endingState = CompletedReason.SUCCEEDED }
                .build()
            addAllRequisitions(REQUISITIONS)
          }
          .build()
      )
    assertThat(computationStore.get(blobKey)?.readToString()).isNotEmpty()

    assertThat(systemComputationResult.name).isEqualTo("computations/$GLOBAL_ID")
    // The signature is non-deterministic, so we only verity the encryption is not empty.
    assertThat(systemComputationResult.encryptedResult).isNotEmpty()
    assertThat(systemComputationResult)
      .comparingExpectedFieldsOnly()
      .isEqualTo(
        setComputationResultRequest {
          name = "computations/$GLOBAL_ID"
          aggregatorCertificate = CONSENT_SIGNALING_CERT_DER
          resultPublicKey = ENCRYPTION_PUBLIC_KEY.toByteString()
        }
      )

    assertThat(cryptoRequest)
      .isEqualTo(
        CompleteExecutionPhaseThreeAtAggregatorRequest.newBuilder()
          .apply {
            sameKeyAggregatorMatrix = ByteString.copyFromUtf8("data")
            localElGamalKeyPair = DUCHY_ONE_KEY_PAIR
            curveId = CURVE_ID
            maximumFrequency = MAX_FREQUENCY
            globalFrequencyDpNoisePerBucketBuilder.apply {
              contributorsCount = WORKER_COUNT
              dpParams = TEST_NOISE_CONFIG.frequencyNoiseConfig
            }
          }
          .build()
      )
  }
}

private suspend fun Blob.readToString(): String = read().flatten().toStringUtf8()

private suspend fun ComputationStore.writeString(
  context: ComputationBlobContext,
  content: String
): Blob = write(context, ByteString.copyFromUtf8(content))

private suspend fun RequisitionStore.writeString(
  context: RequisitionBlobContext,
  content: String
): Blob = write(context, ByteString.copyFromUtf8(content))
