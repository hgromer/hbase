/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.hadoop.hbase.backup.impl;

import org.apache.commons.lang3.StringUtils;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.ColumnFamilyDescriptor;
import org.apache.yetus.audience.InterfaceAudience;

@InterfaceAudience.Private
public class ColumnFamilyMismatchException extends BackupException {
  private ColumnFamilyMismatchException(String msg) {
    super(msg);
  }

  public static ColumnFamilyMismatchException create(TableName tn,
    ColumnFamilyDescriptor[] currentCfs, ColumnFamilyDescriptor[] backupCfs) {
    String currentCfsParsed = StringUtils.join(currentCfs, ',');
    String backupCfsParsed = StringUtils.join(backupCfs, ',');

    String msg =
      "Mismatch in column family descriptors for table: %s\nCurrent families: %s\nBackup families: %s"
        .formatted(tn, currentCfsParsed, backupCfsParsed);
    return new ColumnFamilyMismatchException(msg);
  }
}
