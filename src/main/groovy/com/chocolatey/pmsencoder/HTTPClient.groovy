package com.chocolatey.pmsencoder

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import groovy.transform.TypeCheckingMode
import groovy.util.logging.Log4j
import groovy.util.slurpersupport.GPathResult
import groovyx.net.http.ContentType
import groovyx.net.http.HTTPBuilder
import org.apache.http.HttpHost
import org.apache.http.NameValuePair
import org.apache.http.client.methods.HttpUriRequest
import org.apache.http.client.utils.URLEncodedUtils
import org.apache.http.protocol.ExecutionContext
import org.apache.http.protocol.HttpContext

import java.nio.charset.Charset

import static groovyx.net.http.Method.GET
import static groovyx.net.http.Method.HEAD

// return types taken from:
// ParserRegistry: http://tinyurl.com/395cjkb

// XXX the URLENC type can probably be used to simplify YouTube fmt_url_map handling

@CompileStatic
@Log4j(value="logger")
class HTTPClient {
    private static final DEFAULT_CHARSET = 'UTF-8'
    private JsonSlurper jsonSlurper = new JsonSlurper()
    private XmlSlurper xmlSlurper = new XmlSlurper()

    // these methods are called from scripts so need to be forgiving (Object) in what
    // they receive

    public String get(Object uri) { // i.e. get text
        getType(uri?.toString(), ContentType.TEXT)?.toString()
    }

    public GPathResult getXML(Object uri) {
        // XXX using HTTPBuilder's XML converter causes the following error when retrieving a file
        // that loads fine as plain text:
        //
        //     java.util.zip.ZipException: Not in GZIP format
        //
        // return getType(uri?.toString(), ContentType.XML) as GPathResult
        return xmlSlurper.parse(uri?.toString())
    }

    public GPathResult getHTML(Object uri) {
        return getType(uri?.toString(), ContentType.HTML) as GPathResult
    }

    // FIXME unused
    public Map<String, String> getForm(Object uri) {
        return getType(uri?.toString(), ContentType.URLENC) as Map<String, String>
    }

    // TODO JsonSlurper will add a parse(URL) method in Groovy 2.2.0
    public Object getJSON(Object uri) {
        return jsonSlurper.parseText(get(uri))
    }

    // allow the getNameValueX(Object) methods to handle a query string or a URI with a query string
    private static String getQuery(Object str) {
        if (str != null) {
            try {
                // valid URIs include:
                // 1) foo=bar&baz=quux (uri.query is null)
                // 2) http://www.example.com/script?foo=bar&baz=quux (uri.query is foo=bar&baz=quux)
                // if query is defined (e.g. a full URI), return it, otherwise return the string as is
                def uri = new URI(str.toString())
                return uri.query ?: str
            } catch (URISyntaxException ignored) { } // not a full URI or query string
        }

        return str
    }

    public static List<NameValuePair> getNameValuePairs(Object str, String charset = DEFAULT_CHARSET) {
        // parse(String, Charset): introduced in HttpClient 4.2
        return URLEncodedUtils.parse(getQuery(str), Charset.forName(charset))
    }

    public static List<NameValuePair> getNameValuePairs(URI uri, String charset = DEFAULT_CHARSET) {
        return URLEncodedUtils.parse(uri, charset)
    }

    public static Map<String, String> getNameValueMap(Object str, String charset = DEFAULT_CHARSET) {
        /*
            collectEntries (new in Groovy 1.7.9) transforms (via the supplied closure)
            a list of elements into a list of pairs and then
            assembles a map from those pairs. mapBy, mapFrom, or toMapBy might have been a clearer name...
        */
        return getNameValuePairs(getQuery(str), charset).collectEntries { NameValuePair pair -> [ pair.name, pair.value ] }
    }

    public static Map<String, String> getNameValueMap(URI uri, String charset = DEFAULT_CHARSET) {
        return getNameValuePairs(uri, charset).collectEntries { NameValuePair pair -> [ pair.name, pair.value ] }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    private Object getType(String uri, ContentType contentType) {
        // allocate one per request: try to avoid this exception:
        // java.lang.IllegalStateException: Invalid use of SingleClientConnManager: connection still allocated.
        def http = new HTTPBuilder()

        http.request(uri, GET, contentType) { req ->
            // HTTPBuilder cleans up the reader after this closure, so drain it before returning text
            response.success = { resp, result -> contentType == ContentType.TEXT ? result.getText() : result }
            response.failure = { null } // parity (for now) with LWP::Simple
        }
    }

    // TODO: return a Map on success (ignore headers with multiple values?)
    @CompileStatic(TypeCheckingMode.SKIP)
    public boolean head(Object uri) {
        def http = new HTTPBuilder()

        http.request(uri?.toString(), HEAD, ContentType.TEXT) { req ->
            response.success = { true }
            response.failure = { false }
        }
    }

    @CompileStatic(TypeCheckingMode.SKIP)
    public String target(Object uri) {
        def http = new HTTPBuilder()

        http.request(uri?.toString(), HEAD, ContentType.TEXT) { req ->
            response.success = { resp ->
                getTargetURI(resp.getContext())
            }
            response.failure = { null }
        }
    }

    // XXX wtf?! all this to get the destination URI
    // http://hc.apache.org/httpcomponents-client-dev/tutorial/html/httpagent.html#d4e1195
    private static String getTargetURI(HttpContext cxt) {
        def hostURI = (cxt.getAttribute(ExecutionContext.HTTP_TARGET_HOST) as HttpHost).toURI()
        def finalRequest = cxt.getAttribute(ExecutionContext.HTTP_REQUEST) as HttpUriRequest
        def targetURI = null

        try {
            def hostURL = new URI(hostURI).toURL()
            targetURI = new URL(hostURL, finalRequest.getURI().toString()).toExternalForm()
        } catch (Exception e) {
            logger.error("can't determine target URI: $e")
        }

        return targetURI
    }
}
