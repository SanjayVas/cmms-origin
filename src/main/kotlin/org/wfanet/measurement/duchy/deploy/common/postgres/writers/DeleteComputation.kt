// Copyright 2023 The Cross-Media Measurement Authors
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

package org.wfanet.measurement.duchy.deploy.common.postgres.writers

import org.wfanet.measurement.common.db.r2dbc.boundStatement
import org.wfanet.measurement.common.db.r2dbc.postgres.PostgresWriter

/**
 * [PostgresWriter] to delete a computation by its localComputationId.
 *
 * @param localId local identifier of a computation.
 */
class DeleteComputation(private val localId: Long) : PostgresWriter<Unit>() {

  override suspend fun TransactionScope.runTransaction() {
    val statement =
      boundStatement(
        """
        DELETE FROM Computations
        WHERE ComputationId = $1
      """
          .trimIndent()
      ) {
        bind("$1", localId)
      }
    transactionContext.executeStatement(statement)
  }
}
