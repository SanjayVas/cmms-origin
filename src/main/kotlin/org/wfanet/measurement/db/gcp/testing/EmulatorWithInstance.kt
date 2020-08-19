package org.wfanet.measurement.db.gcp.testing

import com.google.cloud.spanner.DatabaseClient
import com.google.cloud.spanner.DatabaseId
import com.google.cloud.spanner.Instance
import com.google.cloud.spanner.InstanceConfigId
import com.google.cloud.spanner.InstanceId
import com.google.cloud.spanner.InstanceInfo
import com.google.cloud.spanner.Spanner
import com.google.cloud.spanner.SpannerOptions

/**
 * [AutoCloseable] wrapping a [SpannerEmulator] with a single test [Instance].
 */
internal class EmulatorWithInstance : AutoCloseable {
  private val spannerEmulator = SpannerEmulator().apply { start() }

  private val spanner: Spanner
  init {
    val emulatorHost = spannerEmulator.blockUntilReady()

    val spannerOptions =
      SpannerOptions.newBuilder().setProjectId(PROJECT_ID).setEmulatorHost(emulatorHost).build()
    spanner = spannerOptions.service
  }

  val instance: Instance
  init {
    instance = spanner.instanceAdminClient.createInstance(
      InstanceInfo.newBuilder(InstanceId.of(PROJECT_ID, INSTANCE_NAME))
        .setDisplayName(INSTANCE_DISPLAY_NAME)
        .setInstanceConfigId(InstanceConfigId.of(PROJECT_ID, INSTANCE_CONFIG))
        .setNodeCount(1)
        .build()
    ).get()
  }

  override fun close() {
    instance.delete()
    spanner.close()
    spannerEmulator.close()
  }

  fun getDatabaseClient(databaseId: DatabaseId): DatabaseClient =
    spanner.getDatabaseClient(databaseId)

  companion object {
    private const val PROJECT_ID = "test-project"
    private const val INSTANCE_NAME = "test-instance"
    private const val INSTANCE_DISPLAY_NAME = "Test Instance"
    private const val INSTANCE_CONFIG = "emulator-config"
  }
}
