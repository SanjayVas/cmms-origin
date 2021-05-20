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

#ifndef SRC_MAIN_CC_WFANET_PANELMATCH_PROTOCOL_CRYPTO_COMMUTATIVE_ENCRYPTION_UTILITY_H_
#define SRC_MAIN_CC_WFANET_PANELMATCH_PROTOCOL_CRYPTO_COMMUTATIVE_ENCRYPTION_UTILITY_H_

#include "absl/status/statusor.h"
#include "wfanet/panelmatch/protocol/crypto/cryptor.pb.h"

namespace wfanet::panelmatch::protocol::crypto {

absl::StatusOr<
    wfanet::panelmatch::protocol::protobuf::ApplyCommutativeEncryptionResponse>
ApplyCommutativeEncryption(const wfanet::panelmatch::protocol::protobuf::
                               ApplyCommutativeEncryptionRequest& request);

absl::StatusOr<wfanet::panelmatch::protocol::protobuf::
                   ReApplyCommutativeEncryptionResponse>
ReApplyCommutativeEncryption(const wfanet::panelmatch::protocol::protobuf::
                                 ReApplyCommutativeEncryptionRequest& request);

absl::StatusOr<
    wfanet::panelmatch::protocol::protobuf::ApplyCommutativeDecryptionResponse>
ApplyCommutativeDecryption(const wfanet::panelmatch::protocol::protobuf::
                               ApplyCommutativeDecryptionRequest& request);

}  // namespace wfanet::panelmatch::protocol::crypto

#endif  // SRC_MAIN_CC_WFANET_PANELMATCH_PROTOCOL_CRYPTO_COMMUTATIVE_ENCRYPTION_UTILITY_H_
