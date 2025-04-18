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
package org.apache.hadoop.hbase.security;

import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.security.sasl.Sasl;
import javax.security.sasl.SaslClient;
import javax.security.sasl.SaslException;
import javax.security.sasl.SaslServer;
import org.apache.hadoop.hbase.util.Bytes;
import org.apache.yetus.audience.InterfaceAudience;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.apache.hbase.thirdparty.com.google.common.base.Splitter;

@InterfaceAudience.Private
public class SaslUtil {
  private static final Logger LOG = LoggerFactory.getLogger(SaslUtil.class);
  public static final String SASL_DEFAULT_REALM = "default";
  public static final int SWITCH_TO_SIMPLE_AUTH = -88;

  public enum QualityOfProtection {
    AUTHENTICATION("auth"),
    INTEGRITY("auth-int"),
    PRIVACY("auth-conf");

    private final String saslQop;

    QualityOfProtection(String saslQop) {
      this.saslQop = saslQop;
    }

    public String getSaslQop() {
      return saslQop;
    }

    public boolean matches(String stringQop) {
      if (saslQop.equals(stringQop)) {
        LOG.warn("Use authentication/integrity/privacy as value for rpc protection "
          + "configurations instead of auth/auth-int/auth-conf.");
        return true;
      }
      return name().equalsIgnoreCase(stringQop);
    }
  }

  /** Splitting fully qualified Kerberos name into parts */
  public static String[] splitKerberosName(String fullName) {
    return Splitter.onPattern("[/@]").splitToStream(fullName).toArray(String[]::new);
  }

  public static String encodeIdentifier(byte[] identifier) {
    return Base64.getEncoder().encodeToString(identifier);
  }

  public static byte[] decodeIdentifier(String identifier) {
    return Base64.getDecoder().decode(Bytes.toBytes(identifier));
  }

  public static char[] encodePassword(byte[] password) {
    return Base64.getEncoder().encodeToString(password).toCharArray();
  }

  /**
   * Returns {@link org.apache.hadoop.hbase.security.SaslUtil.QualityOfProtection} corresponding to
   * the given {@code stringQop} value.
   * @throws IllegalArgumentException If stringQop doesn't match any QOP.
   */
  public static QualityOfProtection getQop(String stringQop) {
    for (QualityOfProtection qop : QualityOfProtection.values()) {
      if (qop.matches(stringQop)) {
        return qop;
      }
    }
    throw new IllegalArgumentException("Invalid qop: " + stringQop
      + ". It must be one of 'authentication', 'integrity', 'privacy'.");
  }

  /**
   * Initialize SASL properties for a given RPC protection level.
   * @param rpcProtection Value of 'hbase.rpc.protection' configuration.
   * @return Map with values for SASL properties.
   */
  public static Map<String, String> initSaslProperties(String rpcProtection) {
    String saslQop;
    if (rpcProtection.isEmpty()) {
      saslQop = QualityOfProtection.AUTHENTICATION.getSaslQop();
    } else {
      StringBuilder saslQopBuilder = new StringBuilder();
      for (String s : Splitter.on(',').split(rpcProtection)) {
        QualityOfProtection qop = getQop(s);
        saslQopBuilder.append(",").append(qop.getSaslQop());
      }
      saslQop = saslQopBuilder.substring(1); // remove first ','
    }
    Map<String, String> saslProps = new TreeMap<>();
    saslProps.put(Sasl.QOP, saslQop);
    saslProps.put(Sasl.SERVER_AUTH, "true");
    return saslProps;
  }

  static void safeDispose(SaslClient saslClient) {
    try {
      saslClient.dispose();
    } catch (SaslException e) {
      LOG.error("Error disposing of SASL client", e);
    }
  }

  static void safeDispose(SaslServer saslServer) {
    try {
      saslServer.dispose();
    } catch (SaslException e) {
      LOG.error("Error disposing of SASL server", e);
    }
  }

  public static void verifyNegotiatedQop(String requestedQopString, String negotiatedQop)
    throws IOException {
    // We use the SASL QOP names here, not the HBase names
    if (requestedQopString == null || requestedQopString.isEmpty()) {
      // None requested, nothing to check
      return;
    }
    List<String> requestedQops = Arrays.asList(requestedQopString.toLowerCase().split(","));
    if (negotiatedQop == null) {
      // Null negotiated QOP is equivalent to "auth" (for mechanisms without QOP support)
      negotiatedQop = QualityOfProtection.AUTHENTICATION.getSaslQop();
    }
    if (requestedQops.contains(negotiatedQop.toLowerCase())) {
      return;
    }
    throw new IOException("Could not negotiate requested SASL QOP. Requested:" + requestedQopString
      + " , negotiated:" + negotiatedQop);
  }
}
