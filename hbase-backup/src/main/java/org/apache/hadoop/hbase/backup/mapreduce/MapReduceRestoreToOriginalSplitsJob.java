package org.apache.hadoop.hbase.backup.mapreduce;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.hbase.TableName;
import org.apache.hadoop.hbase.backup.RestoreJob;
import org.apache.hadoop.hbase.tool.BulkLoadHFiles;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.hadoop.hbase.util.FSVisitor;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@InterfaceAudience.Private
public class MapReduceRestoreToOriginalSplitsJob implements RestoreJob {
  private static final Logger LOG = LoggerFactory.getLogger(MapReduceRestoreToOriginalSplitsJob.class);
  private Configuration conf;

  @Override
  public void run(Path[] dirPaths, TableName[] fromTables, Path restoreRootDir,
    TableName[] toTables, boolean fullBackupRestore) throws IOException {
    Configuration conf = getConf();

    // We are copying directly the snapshot. We should copy the files rather than moving them
    conf.setBoolean(BulkLoadHFiles.ALWAYS_COPY_FILES, true);

    FileSystem fs = FileSystem.get(conf);
    Map<byte[], List<Path>> family2Files = buildFamily2Files(fs, dirPaths, fullBackupRestore);

    BulkLoadHFiles bulkLoad = BulkLoadHFiles.create(conf);
    for (int i = 0; i < fromTables.length; i++) {
      bulkLoad.bulkLoad(toTables[i], family2Files);
    }
  }

  @Override
  public void setConf(Configuration configuration) {
    this.conf = configuration;
  }

  @Override
  public Configuration getConf() {
    return conf;
  }

  private static Map<byte[], List<Path>> buildFamily2Files(FileSystem fs, Path[] dirs, boolean isFullBackup) throws IOException {
    if (isFullBackup) {
      return buildFullBackupFamily2Files(fs, dirs);
    }

    Map<byte[], List<Path>> family2Files = Maps.newHashMap();

    for (Path dir : dirs) {
     byte[] familyName = Bytes.toBytes(dir.getParent().getName());
     if (family2Files.containsKey(familyName)) {
       family2Files.get(familyName).add(dir);
     } else {
       family2Files.put(familyName, Lists.newArrayList(dir));
     }
    }

    return family2Files;
  }


  private static Map<byte[], List<Path>> buildFullBackupFamily2Files(FileSystem fs, Path[] dirs) throws IOException {
    Map<byte[], List<Path>> family2Files = Maps.newHashMap();
    for (Path regionPath : dirs) {
      FSVisitor.visitRegionStoreFiles(fs, regionPath, (region, family, name) -> {
        Path path = new Path(regionPath, new Path(family, name));
        byte[] familyName = Bytes.toBytes(family);
        if (family2Files.containsKey(familyName)) {
          family2Files.get(familyName).add(path);
        } else {
          family2Files.put(familyName, Lists.newArrayList(path));
        }
      });
    }
    return family2Files;
  }

}
