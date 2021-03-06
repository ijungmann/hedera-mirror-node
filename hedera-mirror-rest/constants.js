/*-
 * ‌
 * Hedera Mirror Node
 *​
 * Copyright (C) 2019 - 2020 Hedera Hashgraph, LLC
 *​
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

'use strict';

// url query filer keys
const filterKeys = {
  ACCOUNT_ID: 'account.id',
  ACCOUNT_BALANCE: 'account.balance',
  ACCOUNT_PUBLICKEY: 'account.publickey',
  ENCODING: 'encoding',
  ENTITY_PUBLICKEY: 'publickey',
  LIMIT: 'limit',
  ORDER: 'order',
  RESULT: 'result',
  SEQUENCE_NUMBER: 'sequencenumber',
  TIMESTAMP: 'timestamp',
  TOKENID: 'tokenid',
  TOKEN_ID: 'token.id',
  CREDIT_TYPE: 'type',
  TRANSACTION_TYPE: 'transactiontype',
};

// sql table columns
const entityColumns = {
  ENTITY_NUM: 'entity_num',
  ENTITY_REALM: 'entity_realm',
  ENTITY_SHARD: 'entity_shard',
  PUBLIC_KEY: 'ed25519_public_key_hex',
};

const transactionColumns = {
  TYPE: 'type',
};

const responseDataLabel = 'mirrorRestData';

const orderFilterValues = {
  ASC: 'asc',
  DESC: 'desc',
};

// topic messages filter options
const characterEncoding = {
  BASE64: 'base64',
  UTF8: 'utf-8',
};

const transactionResultFilter = {
  SUCCESS: 'success',
  FAIL: 'fail',
};

const cryptoTransferType = {
  CREDIT: 'credit',
  DEBIT: 'debit',
};

const cloudProviders = {
  S3: 'S3',
  GCP: 'GCP',
};

const defaultCloudProviderEndpoints = {
  [cloudProviders.S3]: 'https://s3.amazonaws.com',
  [cloudProviders.GCP]: 'https://storage.googleapis.com',
};

const networks = {
  DEMO: 'DEMO',
  MAINNET: 'MAINNET',
  TESTNET: 'TESTNET',
  PREVIEWNET: 'PREVIEWNET',
  OTHER: 'OTHER',
};

const defaultBucketNames = {
  [networks.DEMO]: 'hedera-demo-streams',
  [networks.MAINNET]: 'hedera-mainnet-streams',
  [networks.TESTNET]: 'hedera-stable-testnet-streams-2020-08-27',
  [networks.PREVIEWNET]: 'hedera-preview-testnet-streams',
  [networks.OTHER]: null,
};

const recordStreamPrefix = 'recordstreams/record';

const transactionTypes = {
  UNKNOWN: -1,
  CONTRACTCALL: 7,
  CONTRACTCREATEINSTANCE: 8,
  CONTRACTUPDATEINSTANCE: 9,
  CRYPTOADDLIVEHASH: 10,
  CRYPTOCREATEACCOUNT: 11,
  CRYPTODELETE: 12,
  CRYPTODELETELIVEHASH: 13,
  CRYPTOTRANSFER: 14,
  CRYPTOUPDATEACCOUNT: 15,
  FILEAPPEND: 16,
  FILECREATE: 17,
  FILEDELETE: 18,
  FILEUPDATE: 19,
  SYSTEMDELETE: 20,
  SYSTEMUNDELETE: 21,
  CONTRACTDELETEINSTANCE: 22,
  FREEZE: 23,
  CONSENSUSCREATETOPIC: 24,
  CONSENSUSUPDATETOPIC: 25,
  CONSENSUSDELETETOPIC: 26,
  CONSENSUSSUBMITMESSAGE: 27,
  UNCHECKEDSUBMIT: 28,
  TOKENCREATION: 29,
  TOKENTRANSFERS: 30,
  TOKENFREEZE: 31,
  TOKENUNFREEZE: 32,
  TOKENGRANTKYC: 33,
  TOKENREVOKEKYC: 34,
  TOKENDELETION: 35,
  TOKENUPDATE: 36,
  TOKENMINT: 37,
  TOKENBURN: 38,
  TOKENWIPE: 39,
  TOKENASSOCIATE: 40,
  TOKENDISSOCIATE: 41,
};

module.exports = {
  characterEncoding,
  cloudProviders,
  cryptoTransferType,
  defaultBucketNames,
  defaultCloudProviderEndpoints,
  entityColumns,
  filterKeys,
  networks,
  orderFilterValues,
  recordStreamPrefix,
  responseDataLabel,
  transactionColumns,
  transactionResultFilter,
  transactionTypes,
};
