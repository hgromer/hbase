package org.apache.hadoop.hbase.backup.master;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import org.apache.hadoop.hbase.backup.BackupInfo;
import org.apache.hadoop.hbase.net.Address;

public class ServerPreservationBoundariesBuilder {
  private final Map<Address, TsAndBackups> boundaries = new HashMap<>();
  private final Set<String> backups = new HashSet<>();

  public void add(BackupInfo backup, Address address, long ts) {
    TsAndBackups tsAndBackups = boundaries.computeIfAbsent(address, ignore -> new TsAndBackups());
    tsAndBackups.backups.add(backup.getBackupId());
    if (ts < tsAndBackups.ts) {
      tsAndBackups.ts = ts;
    }
    backups.add(backup.getBackupId());
  }

  public Map<Address, Long> build() {
    Map<Address, Long> results = new HashMap<>();
    for (Map.Entry<Address, TsAndBackups> entry : boundaries.entrySet()) {
      if (entry.getValue().backups.size() == backups.size()) {
        results.put(entry.getKey(), entry.getValue().ts);
      }
    }
    return results;
  }

  private static class TsAndBackups {
    private final Set<String> backups = new HashSet<>();
    private long ts = Long.MAX_VALUE;
  }
}
