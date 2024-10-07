package org.apache.hadoop.hbase.backup.impl;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.LocatedFileStatus;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.fs.RemoteIterator;
import org.apache.hadoop.hbase.CellUtil;
import org.apache.hadoop.hbase.ExtendedCell;
import org.apache.hadoop.hbase.io.hfile.HFile;
import org.apache.hadoop.hbase.io.hfile.HFileScanner;
import org.apache.hadoop.hbase.util.Bytes;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Helper {
  private static final Logger LOG = LoggerFactory.getLogger(Helper.class);

  public static List<LocatedFileStatus> listAll(Configuration conf, Path path) throws IOException {
    List<LocatedFileStatus> all = new ArrayList<>();

    RemoteIterator<LocatedFileStatus> files = FileSystem.get(conf).listFiles(path, true);
    while (files.hasNext()) {
      all.add(files.next());
    }

    return all;
  }

  public static void read(Configuration conf, LocatedFileStatus file) throws IOException {
    FileSystem fs = FileSystem.get(conf);
    Set<String> bulkloadRows = new HashSet<>();

    if (!HFile.isHFileFormat(fs, file.getPath())) {
      return;
    }

    try (HFile.Reader reader = HFile.createReader(fs, file.getPath(), conf)) {
      if (reader.getEntries() == 0) {
        return;
      }

      HFileScanner scanner = reader.getScanner(conf, false, false);
      scanner.seekTo();
      ExtendedCell key = scanner.getKey();
      String val = Bytes.toString(CellUtil.copyRow(key));

      if (val.contains("bulkload")) {
        bulkloadRows.add(val);
      }

      while (scanner.next()) {
        val = Bytes.toString(CellUtil.copyRow(key));

        if (val.contains("bulkload")) {
          bulkloadRows.add(val);
        }
      }

      if (!bulkloadRows.isEmpty()) {
        LOG.info("Bulkload rows is {}", bulkloadRows);
      }
    }
  }
}

