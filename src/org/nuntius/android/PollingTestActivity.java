package org.nuntius.android;

import java.util.ArrayList;
import java.util.List;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.nuntius.android.client.EndpointServer;
import org.nuntius.android.client.PollingClient;
import org.nuntius.android.client.RequestClient;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import com.loopj.android.http.AsyncHttpResponseHandler;

import android.app.Activity;
import android.os.Bundle;
import android.util.Log;
import android.widget.ScrollView;
import android.widget.TextView;

public class PollingTestActivity extends Activity {

    private PollingClient polling;
    private RequestClient request;
    private List<String> received;
    private boolean running;

    private static final String testToken =
        "owGbwMvMwCGYeiJtndUThX+Mp6OSmHXVLH1v/7yaam6cmmiclGpknGJg" +
        "YmJpammeaGGUbGaUmmJpYZhmamGSaJpsaGGYVGNsbOJi4GZm7GZsZm7kaGD" +
        "qZmloZmLhamFm6mxhZuboauzoamJk4ObaEcfCIMjBwMbKBDKegYtTAGbtrd" +
        "UM/13clz3N8TknL2M/mXkXz9NXm1YyubvEqvfHsxjdXHVK2YiRYWtq+vYTn" +
        "YbJ/js6NMQOdj1tDbpXU8Dy4fn1jNvM8fNFHQA=";

    private ScrollView getScrollView() {
        return (ScrollView) findViewById(R.id.scroll_main);
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.polling_test);

        received = new ArrayList<String>();

        EndpointServer server = new EndpointServer("http://10.0.2.2/serverimpl1");

        request = new RequestClient(this, server);
        request.setAuthToken(testToken);
        request.setResponseHandler(new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(HttpResponse response) {
                Log.w("PollingTest", "response received");
                TextView wText = (TextView) findViewById(R.id.incoming_text);

                try {
                    HttpEntity ent = response.getEntity();
                    String s = EntityUtils.toString(ent);

                    wText.append("Response:\n" + s + "\n");
                    getScrollView().post(new Runnable() {
                        @Override
                        public void run() {
                            getScrollView().fullScroll(ScrollView.FOCUS_DOWN);
                        }
                    });

                }
                catch (Exception e) {
                    Log.e("PollingTest", "parse error", e);
                }
            }
            @Override
            public void onFailure(Throwable error) {
                Log.e("PollingTest", "request error", error);
            }
        });

        polling = new PollingClient(this, server);
        polling.setAuthToken(testToken);
        polling.setResponseHandler(new AsyncHttpResponseHandler() {
            @Override
            public void onSuccess(HttpResponse response) {
                Log.w("PollingTest", "response received");
                TextView wText = (TextView) findViewById(R.id.incoming_text);

                try {
                    DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
                    DocumentBuilder builder = factory.newDocumentBuilder();
                    Document doc = builder.parse(response.getEntity().getContent());
                    Element body = doc.getDocumentElement();
                    NodeList children = body.getChildNodes();
                    ciao: for (int i = 0; i < children.getLength(); i++) {
                        Element node = (Element) children.item(i);
                        if ("m".equals(node.getNodeName())) {
                            String id = null;
                            String from = null;
                            String text = null;
                            String mime = null;
                            List<String> group = new ArrayList<String>();

                            // messaggio!
                            NodeList msgChildren = node.getChildNodes();
                            for (int j = 0; j < msgChildren.getLength(); j++) {
                                Element n2 = (Element) msgChildren.item(j);
                                if ("i".equals(n2.getNodeName())) {
                                    id = n2.getFirstChild().getNodeValue();
                                    if (received.contains(id)) {
                                        continue ciao;
                                    }

                                    received.add(id);
                                }
                                else if ("s".equals(n2.getNodeName()))
                                    from = n2.getFirstChild().getNodeValue();
                                else if ("c".equals(n2.getNodeName())) {
                                    text = n2.getFirstChild().getNodeValue();
                                    mime = n2.getAttribute("t");
                                }
                                else if ("g".equals(n2.getNodeName()))
                                    group.add(n2.getFirstChild().getNodeValue());
                            }

                            if (id != null && from != null && text != null && mime != null) {
                                wText.append(
                                    "=== Message ===\n" +
                                    "ID: " + id + "\n" +
                                    "From: " + from + "\n" +
                                    "Group: " + group + "\n" +
                                    "Type: " + mime + "\n" +
                                    "Content: " + text + "\n" +
                                    "\n"
                                );
                                getScrollView().post(new Runnable() {
                                    @Override
                                    public void run() {
                                        getScrollView().fullScroll(ScrollView.FOCUS_DOWN);
                                    }
                                });
                                request.request("received", new String[] { "i", id }, null);
                            }
                        }
                    }
                }
                catch (Exception e) {
                    Log.e("PollingTest", "parse error", e);
                }

                if (running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {}
                    if (!running)
                        polling.polling();
                }
            }
            @Override
            public void onFailure(Throwable error) {
                Log.e("PollingTest", "polling error", error);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();

        // start polling :)
        running = true;
        polling.polling();
    }

    @Override
    protected void onPause() {
        super.onPause();

        running = false;
    }
}
