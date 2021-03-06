/**
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
package org.apache.flume.auth;

import java.io.File;
import java.io.IOException;
import java.util.Properties;

import org.apache.hadoop.minikdc.MiniKdc;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;

public class TestFlumeAuthenticator {

  private static MiniKdc kdc;
  private static File workDir;
  private static File flumeKeytab;
  private static String flumePrincipal = "flume/localhost";
  private static File aliceKeytab;
  private static String alicePrincipal = "alice";
  private static Properties conf;

  @BeforeClass
  public static void startMiniKdc() throws Exception {
    workDir = new File(System.getProperty("test.dir", "target"),
            TestFlumeAuthenticator.class.getSimpleName());
    flumeKeytab = new File(workDir, "flume.keytab");
    aliceKeytab = new File(workDir, "alice.keytab");
    conf = MiniKdc.createConf();

    kdc = new MiniKdc(conf, workDir);
    kdc.start();

    kdc.createPrincipal(flumeKeytab, flumePrincipal);
    flumePrincipal = flumePrincipal + "@" + kdc.getRealm();

    kdc.createPrincipal(aliceKeytab, alicePrincipal);
    alicePrincipal = alicePrincipal + "@" + kdc.getRealm();
  }

  @AfterClass
  public static void stopMiniKdc() {
    if (kdc != null) {
      kdc.stop();
    }
  }

  @Test
  public void testNullLogin() throws IOException {
    String principal = null;
    String keytab = null;

    FlumeAuthenticator authenticator = FlumeAuthenticationUtil.getAuthenticator(
            principal, keytab);
    assertFalse(authenticator.isAuthenticated());
  }

  @Test
  public void testFlumeLogin() throws IOException {
    String principal = flumePrincipal;
    String keytab = flumeKeytab.getAbsolutePath();
    String expResult = principal;

    FlumeAuthenticator authenticator = FlumeAuthenticationUtil.getAuthenticator(
            principal, keytab);
    assertTrue(authenticator.isAuthenticated());

    String result = ((KerberosAuthenticator)authenticator).getUserName();
    assertEquals("Initial login failed", expResult, result);

    authenticator = FlumeAuthenticationUtil.getAuthenticator(
            principal, keytab);
    result = ((KerberosAuthenticator)authenticator).getUserName();
    assertEquals("Re-login failed", expResult, result);

    principal = alicePrincipal;
    keytab = aliceKeytab.getAbsolutePath();
    try {
      authenticator = FlumeAuthenticationUtil.getAuthenticator(
              principal, keytab);
      result = ((KerberosAuthenticator)authenticator).getUserName();
      fail("Login should have failed with a new principal: " + result);
    } catch (Exception ex) {
      assertTrue("Login with a new principal failed, but for an unexpected "
          + "reason: " + ex.getMessage(),
          ex.getMessage().contains("Cannot use multiple kerberos principals"));
    }
  }

  @Test
  public void testProxyAs() throws IOException {
    String username = "alice";

    String expResult = username;
    FlumeAuthenticator authenticator = FlumeAuthenticationUtil.getAuthenticator(
            null, null);
    String result = ((UGIExecutor)(authenticator.proxyAs(username))).getUserName();
    assertEquals("Proxy as didn't generate the expected username", expResult, result);

    authenticator = FlumeAuthenticationUtil.getAuthenticator(
            flumePrincipal, flumeKeytab.getAbsolutePath());

    String login = ((KerberosAuthenticator)authenticator).getUserName();
    assertEquals("Login succeeded, but the principal doesn't match",
        flumePrincipal, login);

    result = ((UGIExecutor)(authenticator.proxyAs(username))).getUserName();
    assertEquals("Proxy as didn't generate the expected username", expResult, result);
  }

}
