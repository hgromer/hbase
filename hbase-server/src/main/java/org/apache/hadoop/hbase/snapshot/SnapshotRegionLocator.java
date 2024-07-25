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
package org.apache.hadoop.hbase.snapshot;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.apache.hadoop.hbase.HRegionLocation;
import org.apache.hadoop.hbase.ServerName;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.client.RegionInfo;
import org.apache.hadoop.hbase.client.RegionInfoBuilder;
import org.apache.hadoop.hbase.client.RegionLocator;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.yetus.audience.InterfaceAudience;

import org.apache.hadoop.hbase.shaded.protobuf.generated.HBaseProtos;
import org.apache.hadoop.hbase.shaded.protobuf.generated.SnapshotProtos;

@InterfaceAudience.Private
public class SnapshotRegionLocator implements RegionLocator {

  private static final ServerName DUMMY_SERVER =
    ServerName.parseServerName("www.hbase.com,1234,1212121212");

  private final TableName tableName;
  private final TreeMap<byte[], HRegionReplicas> regions;

  private final List<HRegionLocation> rawLocations;

  public static SnapshotRegionLocator create(SnapshotManifest manifest) {
    TableName tableName = manifest.getTableDescriptor().getTableName();
    TreeMap<byte[], HRegionReplicas> replicas = new TreeMap<>(Bytes.BYTES_COMPARATOR);
    List<HRegionLocation> rawLocations = new ArrayList<>();

    for (SnapshotProtos.SnapshotRegionManifest region : manifest.getRegionManifests()) {
      HBaseProtos.RegionInfo ri = region.getRegionInfo();
      byte[] key = ri.getStartKey().toByteArray();

      HRegionLocation location = toLocation(ri, tableName);
      rawLocations.add(location);
      HRegionReplicas hrr = replicas.get(key);

      if (hrr == null) {
        hrr = new HRegionReplicas(location);
      } else {
        hrr.addReplica(location);
      }

      replicas.put(key, hrr);
    }

    return new SnapshotRegionLocator(tableName, replicas, rawLocations);
  }

  private SnapshotRegionLocator(TableName tableName, TreeMap<byte[], HRegionReplicas> regions,
    List<HRegionLocation> rawLocations) {
    this.tableName = tableName;
    this.regions = regions;
    this.rawLocations = rawLocations;
  }

  @Override
  public HRegionLocation getRegionLocation(byte[] row, int replicaId, boolean reload)
    throws IOException {
    return regions.floorEntry(row).getValue().getReplica(replicaId);
  }

  @Override
  public List<HRegionLocation> getRegionLocations(byte[] row, boolean reload) throws IOException {
    return List.of(getRegionLocation(row, reload));
  }

  @Override
  public void clearRegionLocationCache() {

  }

  @Override
  public List<HRegionLocation> getAllRegionLocations() throws IOException {
    return rawLocations;
  }

  @Override
  public TableName getName() {
    return tableName;
  }

  @Override
  public void close() throws IOException {

  }

  private static HRegionLocation toLocation(HBaseProtos.RegionInfo ri, TableName tableName) {
    RegionInfo region = RegionInfoBuilder.newBuilder(tableName)
      .setStartKey(ri.getStartKey().toByteArray()).setEndKey(ri.getEndKey().toByteArray())
      .setRegionId(ri.getRegionId()).setReplicaId(ri.getReplicaId()).build();

    return new HRegionLocation(region, DUMMY_SERVER);
  }

  private static class HRegionReplicas {
    private final Map<Integer, HRegionLocation> replicas = new HashMap<>();

    private HRegionReplicas(HRegionLocation region) {
      addReplica(region);
    }

    private void addReplica(HRegionLocation replica) {
      this.replicas.put(replica.getRegion().getReplicaId(), replica);
    }

    private HRegionLocation getReplica(int replicaId) {
      return replicas.get(replicaId);
    }
  }
}
