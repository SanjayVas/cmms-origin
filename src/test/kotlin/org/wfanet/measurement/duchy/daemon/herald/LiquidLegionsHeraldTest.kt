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

package org.wfanet.measurement.duchy.daemon.herald

import com.google.common.truth.Truth.assertThat
import com.nhaarman.mockitokotlin2.UseConstructor
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.stub
import java.time.Clock
import kotlin.test.assertFails
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.JUnit4
import org.wfanet.measurement.common.Duchy
import org.wfanet.measurement.common.DuchyOrder
import org.wfanet.measurement.common.grpc.testing.GrpcTestServerRule
import org.wfanet.measurement.common.testing.pollFor
import org.wfanet.measurement.common.throttler.testing.FakeThrottler
import org.wfanet.measurement.duchy.db.computation.testing.FakeComputationDb
import org.wfanet.measurement.duchy.service.internal.computation.ComputationsService
import org.wfanet.measurement.duchy.service.internal.computation.newEmptyOutputBlobMetadata
import org.wfanet.measurement.duchy.service.internal.computation.newInputBlobMetadata
import org.wfanet.measurement.duchy.service.internal.computation.newPassThroughBlobMetadata
import org.wfanet.measurement.duchy.toProtocolStage
import org.wfanet.measurement.internal.duchy.ComputationDetails
import org.wfanet.measurement.internal.duchy.ComputationStageDetails
import org.wfanet.measurement.internal.duchy.ComputationsGrpcKt.ComputationsCoroutineStub
import org.wfanet.measurement.protocol.LiquidLegionsSketchAggregationV1
import org.wfanet.measurement.protocol.LiquidLegionsSketchAggregationV1.Stage.TO_ADD_NOISE
import org.wfanet.measurement.protocol.LiquidLegionsSketchAggregationV1.Stage.TO_CONFIRM_REQUISITIONS
import org.wfanet.measurement.protocol.LiquidLegionsSketchAggregationV1.Stage.WAIT_TO_START
import org.wfanet.measurement.protocol.RequisitionKey
import org.wfanet.measurement.system.v1alpha.GlobalComputation
import org.wfanet.measurement.system.v1alpha.GlobalComputationsGrpcKt.GlobalComputationsCoroutineImplBase
import org.wfanet.measurement.system.v1alpha.GlobalComputationsGrpcKt.GlobalComputationsCoroutineStub
import org.wfanet.measurement.system.v1alpha.MetricRequisitionKey
import org.wfanet.measurement.system.v1alpha.StreamActiveGlobalComputationsResponse

@RunWith(JUnit4::class)
internal class LiquidLegionsHeraldTest {

  private val globalComputations: GlobalComputationsCoroutineImplBase =
    mock(useConstructor = UseConstructor.parameterless()) {}
  private val duchyName = "BOHEMIA"
  private val otherDuchyNames = listOf("SALZBURG", "AUSTRIA")
  private val fakeComputationStorage = FakeComputationDb()
  private val duchyOrder = DuchyOrder(
    setOf(
      Duchy("BOHEMIA", 10L.toBigInteger()),
      Duchy("SALZBURG", 200L.toBigInteger()),
      Duchy("AUSTRIA", 303L.toBigInteger())
    )
  )
  private val primaryComputationDetails = ComputationDetails.newBuilder().apply {
    liquidLegionsV1Builder.apply {
      role = LiquidLegionsSketchAggregationV1.ComputationDetails.RoleInComputation.PRIMARY
      primaryNodeId = "BOHEMIA"
      incomingNodeId = "SALZBURG"
      outgoingNodeId = "AUSTRIA"
    }
  }.build()

  private val secondComputationDetails = ComputationDetails.newBuilder().apply {
    liquidLegionsV1Builder.apply {
      role = LiquidLegionsSketchAggregationV1.ComputationDetails.RoleInComputation.SECONDARY
      primaryNodeId = "BOHEMIA"
      incomingNodeId = "SALZBURG"
      outgoingNodeId = "BOHEMIA"
    }
  }.build()

  @get:Rule
  val grpcTestServerRule = GrpcTestServerRule {
    addService(globalComputations)
    addService(
      ComputationsService(
        fakeComputationStorage,
        globalComputationsStub,
        duchyName,
        Clock.systemUTC(),
        duchyOrder
      )
    )
  }

  private val storageServiceStub: ComputationsCoroutineStub by lazy {
    ComputationsCoroutineStub(grpcTestServerRule.channel)
  }

  private val globalComputationsStub: GlobalComputationsCoroutineStub by lazy {
    GlobalComputationsCoroutineStub(grpcTestServerRule.channel)
  }

  private lateinit var herald: LiquidLegionsHerald
  @Before
  fun initHerald() {
    herald = LiquidLegionsHerald(otherDuchyNames, storageServiceStub, globalComputationsStub)
  }

  @Test
  fun `syncStatuses on empty stream retains same computaiton token`() = runBlocking {
    mockStreamActiveComputationsToReturn(/* No items in stream. */)
    assertThat(herald.syncStatuses("TOKEN_OF_LAST_ITEM")).isEqualTo("TOKEN_OF_LAST_ITEM")
    assertThat(fakeComputationStorage).isEmpty()
  }

  @Test
  fun `syncStatuses creates new computations`() = runBlocking {
    val confirmingKnown = ComputationAtKingdom("454647484950", GlobalComputation.State.CONFIRMING)
    val newComputationsRequisitions = listOf(
      "alice/a/1234",
      "bob/bb/abc",
      "caroline/ccc/234567"
    )
    val confirmingUnknown =
      ComputationAtKingdom("321", GlobalComputation.State.CONFIRMING, newComputationsRequisitions)
    mockStreamActiveComputationsToReturn(confirmingKnown, confirmingUnknown)

    fakeComputationStorage.addComputation(
      globalId = confirmingKnown.globalId,
      stage = TO_CONFIRM_REQUISITIONS.toProtocolStage(),
      computationDetails = primaryComputationDetails,
      blobs = listOf(newInputBlobMetadata(0L, "input-blob"), newEmptyOutputBlobMetadata(1L))
    )

    assertThat(herald.syncStatuses(EMPTY_TOKEN)).isEqualTo(confirmingUnknown.continuationToken)
    assertThat(
      fakeComputationStorage
        .mapValues { (_, fakeComputation) -> fakeComputation.computationStage }
    ).containsExactly(
      confirmingKnown.globalId.toLong(),
      TO_CONFIRM_REQUISITIONS.toProtocolStage(),
      confirmingUnknown.globalId.toLong(),
      TO_CONFIRM_REQUISITIONS.toProtocolStage()
    )

    assertThat(fakeComputationStorage[confirmingUnknown.globalId.toLong()]?.stageSpecificDetails)
      .isEqualTo(
        ComputationStageDetails.newBuilder().apply {
          liquidLegionsV1Builder.toConfirmRequisitionsStageDetailsBuilder.apply {
            addKeys(requisitionKey("alice", "a", "1234"))
            addKeys(requisitionKey("bob", "bb", "abc"))
            addKeys(requisitionKey("caroline", "ccc", "234567"))
          }
        }.build()
      )
  }

  @Test
  fun `syncStatuses starts computations in wait_to_start`() = runBlocking<Unit> {
    val waitingToStart = ComputationAtKingdom("42314125676756", GlobalComputation.State.RUNNING)
    val addingNoise = ComputationAtKingdom("231313", GlobalComputation.State.RUNNING)
    mockStreamActiveComputationsToReturn(waitingToStart, addingNoise)

    fakeComputationStorage.addComputation(
      globalId = waitingToStart.globalId,
      stage = WAIT_TO_START.toProtocolStage(),
      computationDetails = secondComputationDetails,
      blobs = listOf(
        newPassThroughBlobMetadata(0L, "local-copy-of-sketches")
      )
    )

    fakeComputationStorage.addComputation(
      globalId = addingNoise.globalId,
      stage = TO_ADD_NOISE.toProtocolStage(),
      computationDetails = primaryComputationDetails,
      blobs = listOf(
        newInputBlobMetadata(0L, "inputs-to-add-noise"),
        newEmptyOutputBlobMetadata(1L)
      )
    )

    assertThat(herald.syncStatuses(EMPTY_TOKEN)).isEqualTo(addingNoise.continuationToken)
    assertThat(
      fakeComputationStorage
        .mapValues { (_, fakeComputation) -> fakeComputation.computationStage }
    ).containsExactly(
      waitingToStart.globalId.toLong(),
      TO_ADD_NOISE.toProtocolStage(),
      addingNoise.globalId.toLong(),
      TO_ADD_NOISE.toProtocolStage()
    )
  }

  @Test
  fun `syncStatuses starts computations with retries`() = runBlocking {
    val computation = ComputationAtKingdom("42314125676756", GlobalComputation.State.RUNNING)
    mockStreamActiveComputationsToReturn(computation)

    fakeComputationStorage.addComputation(
      globalId = computation.globalId,
      stage = TO_CONFIRM_REQUISITIONS.toProtocolStage(),
      computationDetails = secondComputationDetails,
      blobs = listOf(newInputBlobMetadata(0L, "local-copy-of-sketches"))
    )

    assertThat(herald.syncStatuses(EMPTY_TOKEN)).isEqualTo(computation.continuationToken)

    assertThat(
      fakeComputationStorage
        .mapValues { (_, fakeComputation) -> fakeComputation.computationStage }
    ).containsExactly(
      computation.globalId.toLong(),
      TO_CONFIRM_REQUISITIONS.toProtocolStage()
    )

    // Update the state.
    fakeComputationStorage.remove(computation.globalId.toLong())
    fakeComputationStorage.addComputation(
      globalId = computation.globalId,
      stage = WAIT_TO_START.toProtocolStage(),
      computationDetails = secondComputationDetails,
      blobs = listOf(newPassThroughBlobMetadata(0L, "local-copy-of-sketches"))
    )

    // Wait for the background retry to fix the state.
    val finalComputation = pollFor(timeoutMillis = 10_000L) {
      val c = fakeComputationStorage[computation.globalId.toLong()]
      if (c?.computationStage == TO_ADD_NOISE.toProtocolStage()) {
        c
      } else {
        null
      }
    }

    assertThat(finalComputation).isNotNull()
  }

  @Test
  fun `syncStatuses gives up on starting computations`() = runBlocking<Unit> {
    val heraldWithOneRetry = LiquidLegionsHerald(
      otherDuchyNames,
      storageServiceStub,
      globalComputationsStub,
      maxStartAttempts = 2
    )

    val computation = ComputationAtKingdom("42314125676756", GlobalComputation.State.RUNNING)
    mockStreamActiveComputationsToReturn(computation)

    fakeComputationStorage.addComputation(
      globalId = computation.globalId,
      stage = TO_CONFIRM_REQUISITIONS.toProtocolStage(),
      computationDetails = secondComputationDetails,
      blobs = listOf(newInputBlobMetadata(0L, "local-copy-of-sketches"))
    )

    assertFails {
      heraldWithOneRetry.continuallySyncStatuses(FakeThrottler())
    }
  }

  private fun mockStreamActiveComputationsToReturn(vararg computations: ComputationAtKingdom) =
    globalComputations.stub {
      onBlocking { streamActiveGlobalComputations(any()) }
        .thenReturn(computations.toList().map { it.streamedResponse }.asFlow())
    }

  companion object {
    const val EMPTY_TOKEN = ""
  }
}

/** Simple representation of a computation at the kingdom for testing. */
data class ComputationAtKingdom(
  val globalId: String,
  val stateAtKingdom: GlobalComputation.State,
  val requisitionResourceKeys: List<String> = listOf()
) {
  private fun parseResourceKey(stringKey: String): MetricRequisitionKey {
    val (provider, campaign, requisition) = stringKey.split("/")
    return MetricRequisitionKey.newBuilder().apply {
      dataProviderId = provider
      campaignId = campaign
      metricRequisitionId = requisition
    }.build()
  }

  val continuationToken = "token_for_$globalId"
  val streamedResponse: StreamActiveGlobalComputationsResponse =
    StreamActiveGlobalComputationsResponse.newBuilder().apply {
      globalComputationBuilder.apply {
        keyBuilder.apply { globalComputationId = globalId }
        state = stateAtKingdom
        addAllMetricRequisitions(requisitionResourceKeys.map { parseResourceKey(it) })
      }
      continuationToken = this@ComputationAtKingdom.continuationToken
    }.build()
}

private fun requisitionKey(provider: String, campaign: String, requisition: String) =
  RequisitionKey.newBuilder().apply {
    dataProviderId = provider
    campaignId = campaign
    metricRequisitionId = requisition
  }.build()
