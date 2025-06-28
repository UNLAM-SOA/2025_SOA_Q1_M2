package com.app.smartroller;

import android.content.Context;
import android.util.Log;

import java.io.InputStream;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.CertificateFactory;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;

public class SSLUtils {

    public static SSLSocketFactory getSocketFactory(Context context) throws Exception {
        CertificateFactory cf = CertificateFactory.getInstance("X.509");
        InputStream caInput = context.getResources().openRawResource(R.raw.ca);  // tu archivo ca.crt en res/raw
        Certificate ca;
        try {
            ca = cf.generateCertificate(caInput);
            Log.d("MQTT", "CA cargada: " + ((java.security.cert.X509Certificate) ca).getSubjectDN());
        } finally {
            caInput.close();
        }

        KeyStore keyStore = KeyStore.getInstance(KeyStore.getDefaultType());
        keyStore.load(null, null);
        keyStore.setCertificateEntry("ca", ca);

        TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
        tmf.init(keyStore);

        SSLContext contextSSL = SSLContext.getInstance("TLS");
        contextSSL.init(null, tmf.getTrustManagers(), null);
        return contextSSL.getSocketFactory();
    }
}
