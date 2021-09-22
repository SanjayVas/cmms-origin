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

package org.wfanet.panelmatch.client.privatemembership.testing

import com.google.protobuf.ListValue
import org.apache.beam.sdk.values.KV
import org.apache.beam.sdk.values.PCollection
import org.wfanet.panelmatch.client.privatemembership.DecryptedEventData
import org.wfanet.panelmatch.client.privatemembership.DecryptedQueryResult
import org.wfanet.panelmatch.client.privatemembership.EncryptedEventData
import org.wfanet.panelmatch.client.privatemembership.EncryptedQueryResult
import org.wfanet.panelmatch.client.privatemembership.JoinKey
import org.wfanet.panelmatch.client.privatemembership.PrivateMembershipDecryptRequest
import org.wfanet.panelmatch.client.privatemembership.PrivateMembershipDecryptResponse
import org.wfanet.panelmatch.client.privatemembership.PrivateMembershipEncryptResponse
import org.wfanet.panelmatch.client.privatemembership.QueryBundle
import org.wfanet.panelmatch.client.privatemembership.QueryId
import org.wfanet.panelmatch.client.privatemembership.Result
import org.wfanet.panelmatch.client.privatemembership.decryptedQueryResult
import org.wfanet.panelmatch.client.privatemembership.encryptedEventData
import org.wfanet.panelmatch.client.privatemembership.encryptedQueryResult
import org.wfanet.panelmatch.client.privatemembership.privateMembershipDecryptResponse
import org.wfanet.panelmatch.client.privatemembership.resultOf
import org.wfanet.panelmatch.common.beam.join
import org.wfanet.panelmatch.common.beam.keyBy
import org.wfanet.panelmatch.common.beam.kvOf
import org.wfanet.panelmatch.common.beam.map
import org.wfanet.panelmatch.common.beam.parDo
import org.wfanet.panelmatch.common.crypto.SymmetricCryptor
import org.wfanet.panelmatch.common.crypto.testing.FakeSymmetricCryptor
import org.wfanet.panelmatch.common.toByteString

object PlaintextPrivateMembershipCryptorHelper : PrivateMembershipCryptorHelper {

  private val symmetricCryptor: SymmetricCryptor = FakeSymmetricCryptor()

  private fun decodeQueryBundle(queryBundle: QueryBundle): List<ShardedQuery> {
    val queryIdsList = queryBundle.queryIdsList
    val bucketValuesList =
      ListValue.parseFrom(queryBundle.serializedEncryptedQueries).valuesList.map {
        it.stringValue.toInt()
      }
    return queryIdsList.zip(bucketValuesList) { queryId: QueryId, bucketValue: Int ->
      ShardedQuery(requireNotNull(queryBundle.shardId).id, queryId.id, bucketValue)
    }
  }

  override fun makeEncryptedQueryResults(
    encryptedEventData: List<EncryptedEventData>
  ): List<EncryptedQueryResult> {
    return encryptedEventData.map {
      encryptedQueryResult {
        queryId = it.queryId
        shardId = it.shardId
        ciphertexts += resultOf(it.queryId, it.ciphertextsList.single()).toByteString()
      }
    }
  }

  override fun makeEncryptedQueryResults(
    encryptedEventData: PCollection<EncryptedEventData>
  ): PCollection<EncryptedQueryResult> {
    return encryptedEventData.map {
      encryptedQueryResult {
        queryId = it.queryId
        shardId = it.shardId
        ciphertexts += resultOf(it.queryId, it.ciphertextsList.single()).toByteString()
      }
    }
  }

  /** Simple plaintext decrypter. Expects the encryptor to only populate the first ciphertext. */
  override fun decryptQueryResults(
    request: PrivateMembershipDecryptRequest
  ): PrivateMembershipDecryptResponse {
    val encryptedQueryResults = request.encryptedQueryResultsList
    val decryptedResults: List<DecryptedQueryResult> =
      encryptedQueryResults.map { result ->
        val decodedData =
          Result.parseFrom(result.ciphertextsList.single())
            .serializedEncryptedQueryResult
            .toStringUtf8()
        decryptedQueryResult {
          queryId = result.queryId
          shardId = result.shardId
          queryResult = decodedData.toByteString()
        }
      }

    return privateMembershipDecryptResponse { decryptedQueryResults += decryptedResults }
  }

  override fun makeEncryptedEventData(
    plaintexts: List<DecryptedEventData>,
    joinkeys: List<Pair<Int, String>>
  ): List<EncryptedEventData> {
    return plaintexts.zip(joinkeys).map { (plaintexts, joinkeyList) ->
      encryptedEventData {
        queryId = plaintexts.queryId
        shardId = plaintexts.shardId
        ciphertexts +=
          symmetricCryptor.encrypt(joinkeyList.second.toByteString(), plaintexts.plaintext)
      }
    }
  }

  override fun makeEncryptedEventData(
    plaintexts: PCollection<DecryptedEventData>,
    joinkeys: PCollection<KV<QueryId, JoinKey>>
  ): PCollection<EncryptedEventData> {
    return plaintexts.keyBy { it.queryId }.join(joinkeys) {
      queryId: QueryId,
      plaintextList: Iterable<DecryptedEventData>,
      joinkeyList: Iterable<JoinKey> ->
      yield(
        encryptedEventData {
          this.queryId = queryId
          shardId = plaintextList.single().shardId
          ciphertexts +=
            symmetricCryptor.encrypt(joinkeyList.single().key, plaintextList.single().plaintext)
        }
      )
    }
  }

  override fun decodeEncryptedQuery(
    data: PCollection<PrivateMembershipEncryptResponse>
  ): PCollection<KV<QueryId, ShardedQuery>> {
    return data.parDo("Map to ShardedQuery") { response ->
      yieldAll(
        response
          .ciphertextsList
          .asSequence()
          .map { QueryBundle.parseFrom(it) }
          .flatMap { decodeQueryBundle(it) }
          .map { kvOf(it.queryId, ShardedQuery(it.shardId.id, it.queryId.id, it.bucketId.id)) }
      )
    }
  }
}
