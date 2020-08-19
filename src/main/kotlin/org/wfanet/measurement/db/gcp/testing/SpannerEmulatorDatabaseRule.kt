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

package org.wfanet.measurement.db.gcp.testing

import com.google.cloud.spanner.Database
import com.google.cloud.spanner.DatabaseClient
import java.util.concurrent.atomic.AtomicInteger
import org.junit.rules.TestRule
import org.wfanet.measurement.common.testing.CloseableResource

/**
 * JUnit rule exposing a temporary Google Cloud Spanner database.
 *
 * All instances share a single [SpannerEmulator].
 */
class SpannerEmulatorDatabaseRule(schemaResourcePath: String) :
  DatabaseRule by DatabaseRuleImpl(schemaResourcePath)

private interface DatabaseRule : TestRule {
  val databaseClient: DatabaseClient
}

private class DatabaseRuleImpl(schemaResourcePath: String) :
  DatabaseRule,
  CloseableResource<TemporaryDatabase>({ TemporaryDatabase(schemaResourcePath) }) {

  override val databaseClient: DatabaseClient
    get() = resource.databaseClient
}

private class TemporaryDatabase(schemaResourcePath: String) :
  AutoCloseable {

  private val database: Database
  init {
    val databaseName = "test-db-${instanceCounter.incrementAndGet()}"
    val ddl = javaClass.getResource(schemaResourcePath).readText()
    database = createDatabase(emulator.instance, ddl, databaseName)
  }

  val databaseClient: DatabaseClient by lazy {
    emulator.getDatabaseClient(database.id)
  }

  override fun close() {
    database.drop()
  }

  companion object {
    /** Atomic counter to ensure each instance has a unique name. */
    private val instanceCounter = AtomicInteger(0)

    private val emulator = EmulatorWithInstance()
  }
}
